package com.bartmuskala.mykaroohud.datatype

import android.content.Context
import android.os.Build
import android.util.TypedValue
import android.widget.RemoteViews
import com.bartmuskala.mykaroohud.R
import com.bartmuskala.mykaroohud.datatype.shared.FieldColor
import com.bartmuskala.mykaroohud.datatype.shared.FieldState
import com.bartmuskala.mykaroohud.datatype.shared.HUDState
import com.bartmuskala.mykaroohud.datatype.shared.ViewSizeConfig
import com.bartmuskala.mykaroohud.datatype.mykaroohudFieldRemoteViews
import com.bartmuskala.mykaroohud.datatype.shared.powerZone
import com.bartmuskala.mykaroohud.datatype.shared.sparklineBitmapFlow
import com.bartmuskala.mykaroohud.datatype.shared.zoneFieldColor
import com.bartmuskala.mykaroohud.extension.ZoneColorMode
import com.bartmuskala.mykaroohud.extension.ZoneConfig
import com.bartmuskala.mykaroohud.extension.MyKarooHudConfig
import com.bartmuskala.mykaroohud.extension.streamMyKarooHudConfig
import com.bartmuskala.mykaroohud.extension.streamDataFlow
import com.bartmuskala.mykaroohud.extension.streamUserProfile
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.RideState
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UserProfile
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import com.bartmuskala.mykaroohud.datatype.shared.SparklineFrame
import com.bartmuskala.mykaroohud.extension.streamRideState

class MyKarooHudDataType(
    private val karooSystem: KarooSystemService,
    private val applicationContext: Context
) : DataTypeImpl("mykaroohud", "hud") {

    private val wPrimeCalculator = WPrimeCalculator()

    private fun windColor(speedKmh: Double): Int {
        // Stepped color bands based on headwind speed:
        //  >= +30 : dark red    (strong headwind)
        //  +20..<+30: red
        //  +10..<+20: orange
        //  +5 ..<+10: yellow
        //  -5 ..+5 : grey      (roughly neutral)
        // -15 ..<-5 : light green
        // -30 ..<-15: dark green
        //  < -30   : blue      (strong tailwind)
        return when {
            speedKmh >= 30  -> 0xFF8B0000.toInt()  // dark red
            speedKmh >= 20  -> 0xFFCC0000.toInt()  // red
            speedKmh >= 10  -> 0xFFFF8C00.toInt()  // orange
            speedKmh >= 5   -> 0xFFFFE900.toInt()  // yellow
            speedKmh >= -5  -> 0xFF888888.toInt()  // grey (neutral)
            speedKmh >= -15 -> 0xFF66BB6A.toInt()  // light green
            speedKmh >= -30 -> 0xFF1A9850.toInt()  // dark green
            else            -> 0xFF1565C0.toInt()  // blue (very strong tailwind)
        }
    }


    private fun wPrimeColor(percent: Double): Int {
        val factor = percent // 0.0 to 1.0. 0 = Red, 1 = Green
        val RDYLGN_GREEN = Color(0xFF1A9850)
        val RDYLGN_YELLOW = Color(0xFFFFE900)
        val RDYLGN_RED = Color(0xFFD73027)

        val color = if (factor < 0.5) {
            lerp(RDYLGN_RED, RDYLGN_YELLOW, (factor * 2).toFloat())
        } else {
            lerp(RDYLGN_YELLOW, RDYLGN_GREEN, ((factor - 0.5) * 2).toFloat())
        }
        return color.toArgb()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun liveFlow(context: Context): Flow<HUDState> {
        val powerFlow = karooSystem.streamDataFlow(DataType.Type.SMOOTHED_3S_AVERAGE_POWER).onStart { emit(StreamState.Idle) }
        val instantPowerFlow = karooSystem.streamDataFlow(DataType.Type.POWER).onStart { emit(StreamState.Idle) }
        val relativeWindDirFlow = karooSystem.streamDataFlow(DataType.dataTypeId("karoo-headwind", "headwind")).onStart { emit(StreamState.Idle) }
        val windSpeedFlow = karooSystem.streamDataFlow(DataType.dataTypeId("karoo-headwind", "headwindSpeed")).onStart { emit(StreamState.Idle) }

        val sparklineFlow = sparklineBitmapFlow(
            karooSystem, context,
            widthPx = 800,
            heightPx = 80,
            isPreview = false
        ).onStart { emit(SparklineFrame(null, 0f, 5, true)) }

        // RideState controls whether W'prime advances:
        //   Recording → calculator runs normally
        //   Paused / Idle / any other state → freeze calculator (skip advancing time)
        val rideStateFlow = karooSystem.streamRideState().onStart { emit(RideState.Idle) }

        return rideStateFlow.flatMapLatest { rideState ->
            val isRecording = rideState is RideState.Recording

            combine(
                powerFlow,
                instantPowerFlow,
                relativeWindDirFlow,
                windSpeedFlow,
                karooSystem.streamUserProfile(),
                context.streamMyKarooHudConfig(),
                sparklineFlow
            ) { args: Array<Any?> ->

                val pStream    = args[0] as StreamState
                val pInstStream = args[1] as StreamState
                val wDirStream = args[2] as StreamState
                val wSpeedStream = args[3] as StreamState
                val profile    = args[4] as UserProfile
                val config     = args[5] as MyKarooHudConfig
                val sparkline  = args[6] as SparklineFrame

                val pInst = (pInstStream as? StreamState.Streaming)?.dataPoint?.singleValue ?: 0.0
                val p3s   = (pStream   as? StreamState.Streaming)?.dataPoint?.singleValue ?: 0.0
                val relativeWindDir = (wDirStream  as? StreamState.Streaming)?.dataPoint?.singleValue
                val windSpeed       = (wSpeedStream as? StreamState.Streaming)?.dataPoint?.singleValue

                val powerZone = powerZone(p3s, profile.powerZones)
                val powerColor = zoneFieldColor(powerZone, ZoneColorMode.TEXT, profile,
                    ZoneConfig(powerPalette = com.bartmuskala.mykaroohud.datatype.shared.ZonePalette.ZWIFT),
                    isHr = false)
                val middleSlot = FieldState(
                    primary = p3s.roundToInt().toString(),
                    label = "Power",
                    color = powerColor,
                    iconRes = R.drawable.ic_col_power,
                    colorMode = ZoneColorMode.TEXT
                )

                val leftSlot = if (relativeWindDir != null && windSpeed != null) {
                    val windKmh = windSpeed * 3.6
                    val windColorHex = windColor(windKmh)
                    FieldState(
                        primary = windKmh.roundToInt().toString(),
                        label = "Wind",
                        color = FieldColor.Custom(windColorHex),
                        iconRes = R.drawable.ic_col_speed,
                        colorMode = ZoneColorMode.TEXT,
                        windArrowAngle = relativeWindDir
                    )
                } else {
                    FieldState.searching("Wind", R.drawable.ic_col_speed)
                }

                // W'prime: only advance clock when the ride is actively recording.
                // When paused/stopped, pass null for currentTimeMillis to freeze the state.
                val wPrimePercent = if (isRecording) {
                    wPrimeCalculator.calculateWPrimeBalancePercent(
                        powerWatts        = pInst,
                        currentTimeMillis  = System.currentTimeMillis(),
                        cp                = config.cp,
                        wPrimeJoules      = config.wPrimeJoules,
                    )
                } else {
                    // Paused: freeze — return last known value without advancing
                    wPrimeCalculator.frozenPercent(config.cp, config.wPrimeJoules)
                }
                val wPrimeColorHex = wPrimeColor(wPrimePercent)

                val rightSlot = FieldState(
                    primary = "${(wPrimePercent * 100).roundToInt()}%",
                    label = "W' prime",
                    color = FieldColor.Custom(wPrimeColorHex),
                    iconRes = R.drawable.ic_percent,
                    colorMode = ZoneColorMode.TEXT
                )

                // Hide sparkline when not navigating a route/destination
                val sparklineBitmap = if (sparkline.hasRoute) sparkline.bitmap else null

                HUDState(
                    columns = 3,
                    leftSlot = leftSlot,
                    leftColorMode = ZoneColorMode.TEXT,
                    middleSlot = middleSlot,
                    middleColorMode = ZoneColorMode.TEXT,
                    rightSlot = rightSlot,
                    rightColorMode = ZoneColorMode.TEXT,
                    fourthSlot = leftSlot,
                    fourthColorMode = ZoneColorMode.TEXT,
                    profile = profile,
                    sparklineBitmap = sparklineBitmap
                )
            }
        }.onStart {
            Timber.d("liveFlow combine started")
        }.catch { e ->
            Timber.e(e, "Error in liveFlow combine")
        }.onEach {
            Timber.d("liveFlow emitted HUDState")
        }
    }

    private fun previewFlow(context: Context): Flow<HUDState> {
        val sparklineFlow = sparklineBitmapFlow(
            karooSystem, context,
            widthPx = 800,
            heightPx = 80,
            isPreview = true
        )
        return combine(
            karooSystem.streamUserProfile(),
            sparklineFlow
        ) { profile, sparkline ->
            HUDState(
                columns = 3,
                leftSlot = FieldState("↑ -19", "Wind", FieldColor.Custom(windColor(180.0)), R.drawable.ic_col_speed, colorMode = ZoneColorMode.TEXT),
                leftColorMode = ZoneColorMode.TEXT,
                middleSlot = FieldState("250", "Power", zoneFieldColor(4, ZoneColorMode.TEXT, profile, ZoneConfig(powerPalette = com.bartmuskala.mykaroohud.datatype.shared.ZonePalette.ZWIFT), false), R.drawable.ic_col_power, colorMode = ZoneColorMode.TEXT),
                middleColorMode = ZoneColorMode.TEXT,
                rightSlot = FieldState("85%", "W' prime", FieldColor.Custom(wPrimeColor(0.85)), R.drawable.ic_percent, colorMode = ZoneColorMode.TEXT),
                rightColorMode = ZoneColorMode.TEXT,
                fourthSlot = FieldState("", "", FieldColor.Default),
                fourthColorMode = ZoneColorMode.TEXT,
                profile = profile,
                sparklineBitmap = sparkline.bitmap
            )
        }
    }

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        Timber.d("startView called preview=${config.preview}")
        emitter.onNext(UpdateGraphicConfig(showHeader = false))
        val scope = CoroutineScope(Dispatchers.IO + Job())
        emitter.setCancellable { scope.cancel() }
        scope.launch {
            val flow = if (config.preview) previewFlow(context) else liveFlow(context)
            flow.collect { state -> 
                Timber.d("startView collected state, updating view")
                emitter.updateView(renderState(state, config, context)) 
            }
        }
    }

    private fun renderState(state: HUDState, config: ViewConfig, context: Context): RemoteViews {
        val density = context.resources.displayMetrics.density
        val paddingPx = (2f * density).toInt()
        val sparklineHeightPx = (34f * density).toInt()
        val sizeConfig = ViewSizeConfig.HUD_THREE
        val layoutRes = R.layout.mykaroohud_hud
        val rv = RemoteViews(context.packageName, layoutRes)
        val paddingHPx = (4f * density).toInt()
        rv.setViewPadding(R.id.hud_root, paddingHPx, paddingPx, paddingHPx, paddingPx)
        rv.setViewPadding(R.id.hud_slot_row, 0, 0, 0, sparklineHeightPx)

        if (config.preview && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            rv.setViewOutlinePreferredRadius(R.id.hud_root, 12f, TypedValue.COMPLEX_UNIT_DIP)
            rv.setBoolean(R.id.hud_root, "setClipToOutline", true)
        }

        buildList {
            add(Triple(R.id.hud_slot_left,   state.leftSlot,   state.leftColorMode))
            add(Triple(R.id.hud_slot_middle, state.middleSlot, state.middleColorMode))
            add(Triple(R.id.hud_slot_right,  state.rightSlot,  state.rightColorMode))
        }.forEach { (slotId, field, colorMode) ->
            rv.removeAllViews(slotId)
            rv.addView(
                slotId,
                mykaroohudFieldRemoteViews(
                    field      = field,
                    alignment  = config.alignment,
                    colorMode  = colorMode,
                    sizeConfig = sizeConfig,
                    preview    = false,
                    context    = context,
                ),
            )
        }

        if (state.sparklineBitmap != null) {
            rv.setViewVisibility(R.id.hud_sparkline_container, android.view.View.VISIBLE)
            rv.setImageViewBitmap(R.id.hud_elevation_sparkline, state.sparklineBitmap)
        } else {
            rv.setViewVisibility(R.id.hud_sparkline_container, android.view.View.GONE)
        }

        return rv
    }
}
