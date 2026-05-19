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
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UserProfile
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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

class MyKarooHudDataType(
    private val karooSystem: KarooSystemService,
    private val applicationContext: Context
) : DataTypeImpl("mykaroohud", "hud") {

    private val wPrimeCalculator = WPrimeCalculator()

    private fun windColor(angle: Double): Int {
        // angle: 0 = tailwind (dark green), 180 = headwind (dark red), 360 = tailwind (dark green)
        // map 0..180..360 to 0..1..0
        val relativeAngle = if (angle > 180) 360 - angle else angle
        val factor = relativeAngle / 180.0 // 0 = tailwind, 1 = headwind
        // factor 0 = green, 1 = red. We want to interpolate.
        val RDYLGN_GREEN = Color(0xFF1A9850)
        val RDYLGN_YELLOW = Color(0xFFFFE900)
        val RDYLGN_RED = Color(0xFFD73027)

        val color = if (factor < 0.5) {
            lerp(RDYLGN_GREEN, RDYLGN_YELLOW, (factor * 2).toFloat())
        } else {
            lerp(RDYLGN_YELLOW, RDYLGN_RED, ((factor - 0.5) * 2).toFloat())
        }
        return color.toArgb()
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
    private fun getWindArrowRes(diff: Double): Int {
        val icons = arrayOf(
            R.drawable.ic_arrow_s, R.drawable.ic_arrow_sw, R.drawable.ic_arrow_w,
            R.drawable.ic_arrow_nw, R.drawable.ic_arrow_n, R.drawable.ic_arrow_ne,
            R.drawable.ic_arrow_e, R.drawable.ic_arrow_se, R.drawable.ic_arrow_s
        )
        return icons[((diff + 22.5) / 45.0).toInt() % 8]
    }

    private fun getWindArrowString(diff: Double): String {
        val angles = arrayOf("↓", "↙", "←", "↖", "↑", "↗", "→", "↘", "↓")
        return angles[((diff + 22.5) / 45.0).toInt() % 8]
    }

    private fun liveFlow(context: Context): Flow<HUDState> {
        val powerFlow = karooSystem.streamDataFlow(DataType.Type.SMOOTHED_3S_AVERAGE_POWER).onStart { emit(StreamState.Idle) }
        val instantPowerFlow = karooSystem.streamDataFlow(DataType.Type.POWER).onStart { emit(StreamState.Idle) }
        // relativeWindDirFlow: the "headwind" stream from karoo-headwind gives the relative angle
        // (diff) between absolute wind direction and current heading. 0 = tailwind, 180 = headwind.
        val relativeWindDirFlow = karooSystem.streamDataFlow(DataType.dataTypeId("karoo-headwind", "headwind")).onStart { emit(StreamState.Idle) }
        // headwindSpeed: the headwind component in m/s. Negative = tailwind, positive = headwind.
        val windSpeedFlow = karooSystem.streamDataFlow(DataType.dataTypeId("karoo-headwind", "headwindSpeed")).onStart { emit(StreamState.Idle) }
        
        val sparklineFlow = sparklineBitmapFlow(
            karooSystem, context,
            widthPx = 800, // Roughly standard width, will be scaled
            heightPx = 80,
            isPreview = false
        ).onStart { emit(SparklineFrame(null, 0f, 5, true)) }

        return combine(
            powerFlow,
            instantPowerFlow,
            relativeWindDirFlow,
            windSpeedFlow,
            karooSystem.streamUserProfile(),
            context.streamMyKarooHudConfig(),
            sparklineFlow
        ) { args: Array<Any?> ->
            
            val pStream = args[0] as StreamState
            val pInstStream = args[1] as StreamState
            val wDirStream = args[2] as StreamState
            val wSpeedStream = args[3] as StreamState
            val profile = args[4] as UserProfile
            val config = args[5] as MyKarooHudConfig
            val sparkline = args[6] as SparklineFrame

            val pInst = (pInstStream as? StreamState.Streaming)?.dataPoint?.singleValue ?: 0.0
            val p3s = (pStream as? StreamState.Streaming)?.dataPoint?.singleValue ?: 0.0
            val relativeWindDir = (wDirStream as? StreamState.Streaming)?.dataPoint?.singleValue
            val windSpeed = (wSpeedStream as? StreamState.Streaming)?.dataPoint?.singleValue

            val powerZone = powerZone(p3s, profile.powerZones)
            val powerColor = zoneFieldColor(powerZone, ZoneColorMode.TEXT, profile, ZoneConfig(powerPalette = com.bartmuskala.mykaroohud.datatype.shared.ZonePalette.ZWIFT), isHr = false)
            val middleSlot = FieldState(
                primary = p3s.roundToInt().toString(),
                label = "Power",
                color = powerColor,
                iconRes = R.drawable.ic_col_power,
                colorMode = ZoneColorMode.TEXT
            )

            // relativeWindDir: angle in degrees (0-360) between absolute wind direction
            // and current device heading. 0 = wind from behind (tailwind). 180 = headwind.
            // windSpeed from headwindSpeed stream is the headwind component: positive = headwind, negative = tailwind.
            val leftSlot = if (relativeWindDir != null && windSpeed != null) {
                // Use the angle-based color (0=tailwind=green, 180=headwind=red)
                val windColorHex = windColor(relativeWindDir)
                // Display the headwindSpeed value (positive = headwind, negative = tailwind)
                // Arrow shows which direction wind comes FROM relative to rider heading
                FieldState(
                    primary = "${getWindArrowString(relativeWindDir)} ${windSpeed.roundToInt()}",
                    label = "Wind",
                    color = FieldColor.Custom(windColorHex),
                    iconRes = R.drawable.ic_col_speed,
                    colorMode = ZoneColorMode.TEXT
                )
            } else {
                FieldState.searching("Wind", R.drawable.ic_col_speed)
            }

            val currentTimeMillis = System.currentTimeMillis()
            val wPrimePercent = wPrimeCalculator.calculateWPrimeBalancePercent(pInst, currentTimeMillis)
            val wPrimeColorHex = wPrimeColor(wPrimePercent)

            val rightSlot = FieldState(
                primary = "${(wPrimePercent * 100).roundToInt()}%",
                label = "W' prime",
                color = FieldColor.Custom(wPrimeColorHex),
                iconRes = R.drawable.ic_percent,
                colorMode = ZoneColorMode.TEXT
            )

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
                sparklineBitmap = sparkline.bitmap
            )
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
