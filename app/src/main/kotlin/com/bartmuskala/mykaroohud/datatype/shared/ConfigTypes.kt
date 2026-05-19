package com.bartmuskala.mykaroohud.extension

import kotlinx.serialization.Serializable
import com.bartmuskala.mykaroohud.datatype.shared.ZonePalette

@Serializable
enum class ZoneColorMode(val label: String) {
    NONE("None"),
    TEXT("Text"),
    BACKGROUND("Fill"),
}

@Serializable
enum class ElevationSimplification(val label: String, val minAreaM2: Float) {
    NONE("Off", 0f),
    MILD("Mild", 25f),      
    MEDIUM("Medium", 60f),  
    HEAVY("Max", 120f),     
}

@Serializable
enum class SparklineWarp(val label: String, val k: Float, val positionFraction: Float) {
    NONE("Off", 0f, 0.1235f),
    MILD("Mild", 4f, 0.0783f),
    MEDIUM("Medium", 8f, 0.05f),
    HEAVY("Max", 12f, 0.0357f),
}

@Serializable
enum class ElevationZoom(val label: String, val minRangeM: Float) {
    CLOSE("Close", 20f),
    NORMAL("Normal", 50f),
    WIDE("Wide", 100f),
}

@Serializable
data class SparklineConfig(
    val enabled: Boolean = true,
    val lookaheadKm: Int = 5,
    val skipBands: Int = 1,
    val skipBandsDescent: Int = 0,
    val simplification: ElevationSimplification = ElevationSimplification.HEAVY,
    val warp: SparklineWarp = SparklineWarp.MILD,
    val yZoom: ElevationZoom = ElevationZoom.NORMAL,
    val showClimbs: Boolean = true,
    val showPois: Boolean = true,
)

@Serializable
enum class GradePalette(val label: String) {
    KAROO("Karoo"),
    WAHOO("Wahoo"),
    GARMIN("Garmin"),
    HSLUV("HSLuv"),
    ZWIFT("Zwift"),
    TURBO("Turbo"),
}

// Dummy classes to satisfy dependencies that we removed
data class ZoneConfig(
    val gradePalette: GradePalette = GradePalette.KAROO,
    val powerPalette: ZonePalette = ZonePalette.KAROO,
    val hrPalette: ZonePalette = ZonePalette.KAROO,
    val readableColors: Boolean = false,
)
