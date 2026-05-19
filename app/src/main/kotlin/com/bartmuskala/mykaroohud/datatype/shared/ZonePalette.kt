package com.bartmuskala.mykaroohud.datatype.shared

import kotlinx.serialization.Serializable

@Serializable
enum class ZonePalette(val label: String) {
    KAROO("Karoo"),
    WAHOO("Wahoo"),
    INTERVALS("Intervals.icu"),
    ZWIFT("Zwift"),
    HSLUV("HSLuv"),
}
