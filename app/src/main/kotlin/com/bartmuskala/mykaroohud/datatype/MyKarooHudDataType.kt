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
import kotlinx.coroutines.launch
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

    private fun getWindArrow(diff: Double): String {
        val normalized = (diff + 360) % 360
        return when (normalized) {
            in 0.0..22.5, in 337.5..360.0 -> "↓"
            in 22.5..67.5 -> "↙"
            in 67.5..112.5 -> "←"
            in 112.5..157.5 -> "↖"
            in 157.5..202.5 -> "↑"
            in 202.5..247.5 -> "↗"
            in 247.5..292.5 -> "→"
            in 292.5..337.5 -> "↘"
            else -> ""
        }
    }

    private fun liveFlow(context: Context): Flow<HUDState> {
        val powerFlow = karooSystem.streamDataFlow(DataType.Type.SMOOTHED_3S_AVERAGE_POWER)
        val instantPowerFlow = karooSystem.streamDataFlow(DataType.Type.POWER)
        val headingFlow = karooSystem.streamDataFlow(DataType.Type.HEADING)
        val absoluteWindDirFlow = karooSystem.streamDataFlow(DataType.dataTypeId("karoo-headwind", "windDirection"))
        val windSpeedFlow = karooSystem.streamDataFlow(DataType.dataTypeId("karoo-headwind", "windSpeed"))
        
        val sparklineFlow = sparklineBitmapFlow(
            karooSystem, context,
            widthPx = 800, // Roughly standard width, will be scaled
            heightPx = 80,
            isPreview = false
        )

        return combine(
            powerFlow,
            instantPowerFlow,
            headingFlow,
            absoluteWindDirFlow,
            windSpeedFlow,
            karooSystem.streamUserProfile(),
            context.streamMyKarooHudConfig(),
            sparklineFlow
        ) { args: Array<Any?> ->
            
            val pStream = args[0] as StreamState
            val pInstStream = args[1] as StreamState
            val hStream = args[2] as StreamState
            val wDirStream = args[3] as StreamState
            val wSpeedStream = args[4] as StreamState
            val profile = args[5] as UserProfile
            val config = args[6] as MyKarooHudConfig
            val sparkline = args[7] as SparklineFrame

            val pInst = (pInstStream as? StreamState.Streaming)?.dataPoint?.singleValue ?: 0.0
            val p3s = (pStream as? StreamState.Streaming)?.dataPoint?.singleValue ?: 0.0
            val heading = (hStream as? StreamState.Streaming)?.dataPoint?.singleValue
            val absoluteWindDir = (wDirStream as? StreamState.Streaming)?.dataPoint?.singleValue
            val windSpeed = (wSpeedStream as? StreamState.Streaming)?.dataPoint?.singleValue

            val powerZone = powerZone(p3s, profile.powerZones)
            val powerColor = zoneFieldColor(powerZone, ZoneColorMode.BACKGROUND, profile, ZoneConfig(), isHr = false)
            val middleSlot = FieldState(
                primary = p3s.roundToInt().toString(),
                label = "Power",
                color = powerColor,
                iconRes = R.drawable.ic_col_power,
                colorMode = ZoneColorMode.BACKGROUND
            )

            val leftSlot = if (heading != null && absoluteWindDir != null && windSpeed != null) {
                val diff = (absoluteWindDir - heading + 360) % 360
                val relAngle = if (diff > 180) 360 - diff else diff 
                val windColorHex = windColor(relAngle)

                val arrow = getWindArrow(diff)

                FieldState(
                    primary = "${windSpeed.roundToInt()} km/h",
                    label = "$arrow Wind",
                    color = FieldColor.Custom(windColorHex),
                    iconRes = R.drawable.ic_col_speed,
                    colorMode = ZoneColorMode.BACKGROUND
                )
            } else {
                FieldState.searching("Wind", R.drawable.ic_col_speed)
            }

            val currentTimeMillis = System.currentTimeMillis()
            wPrimeCalculator.resetRideState(currentTimeMillis, config.criticalPower, config.wPrimeJoules)
            val wPrimePercent = wPrimeCalculator.calculateWPrimeBalancePercent(pInst, currentTimeMillis)
            val wPrimeColorHex = wPrimeColor(wPrimePercent)

            val rightSlot = FieldState(
                primary = "${(wPrimePercent * 100).roundToInt()}%",
                label = "W' prime",
                color = FieldColor.Custom(wPrimeColorHex),
                iconRes = R.drawable.ic_col_power,
                colorMode = ZoneColorMode.BACKGROUND
            )

            HUDState(
                columns = 3,
                leftSlot = leftSlot,
                leftColorMode = ZoneColorMode.BACKGROUND,
                middleSlot = middleSlot,
                middleColorMode = ZoneColorMode.BACKGROUND,
                rightSlot = rightSlot,
                rightColorMode = ZoneColorMode.BACKGROUND,
                fourthSlot = leftSlot, 
                fourthColorMode = ZoneColorMode.BACKGROUND,
                profile = profile,
                sparklineBitmap = sparkline.bitmap
            )
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
                leftSlot = FieldState("25", "Wind", FieldColor.Custom(windColor(45.0)), R.drawable.ic_col_speed, colorMode = ZoneColorMode.BACKGROUND),
                leftColorMode = ZoneColorMode.BACKGROUND,
                middleSlot = FieldState("250", "Power", zoneFieldColor(4, ZoneColorMode.BACKGROUND, profile, ZoneConfig(), false), R.drawable.ic_col_power, colorMode = ZoneColorMode.BACKGROUND),
                middleColorMode = ZoneColorMode.BACKGROUND,
                rightSlot = FieldState("85%", "W' prime", FieldColor.Custom(wPrimeColor(0.85)), R.drawable.ic_col_power, colorMode = ZoneColorMode.BACKGROUND),
                rightColorMode = ZoneColorMode.BACKGROUND,
                fourthSlot = FieldState("", "", FieldColor.Default),
                fourthColorMode = ZoneColorMode.BACKGROUND,
                profile = profile,
                sparklineBitmap = sparkline.bitmap
            )
        }
    }

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        emitter.onNext(UpdateGraphicConfig(showHeader = false))
        val scope = CoroutineScope(Dispatchers.IO + Job())
        emitter.setCancellable { scope.cancel() }
        scope.launch {
            val flow = if (config.preview) previewFlow(context) else liveFlow(context)
            flow.collect { state -> 
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
