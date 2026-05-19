package com.bartmuskala.mykaroohud.datatype.shared

import com.bartmuskala.mykaroohud.extension.ZoneColorMode
import io.hammerhead.karooext.models.UserProfile
import android.graphics.Bitmap

data class HUDState(
    val columns: Int,
    val leftSlot: FieldState,
    val leftColorMode: ZoneColorMode,
    val middleSlot: FieldState,
    val middleColorMode: ZoneColorMode,
    val rightSlot: FieldState,
    val rightColorMode: ZoneColorMode,
    val fourthSlot: FieldState,
    val fourthColorMode: ZoneColorMode,
    val profile: UserProfile,
    val sparklineBitmap: Bitmap? = null
)
