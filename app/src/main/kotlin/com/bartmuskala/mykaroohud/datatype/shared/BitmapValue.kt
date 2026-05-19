package com.bartmuskala.mykaroohud.datatype.shared

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Typeface
import io.hammerhead.karooext.models.ViewConfig
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private const val MIN_BITMAP_HEIGHT_PX = 30
private const val LETTER_SPACING = -0.04f

/**
 * Constant bitmap height per layout: `0.74 × valueFontBaseSp × density`.
 */
fun valueBitmapHeightPx(valueFontBaseSp: Int, density: Float): Int {
    val raw = (0.74f * valueFontBaseSp * density).toInt()
    return raw.coerceAtLeast(MIN_BITMAP_HEIGHT_PX)
}

/**
 * Draws a navigation-style arrow (like a compass needle / GPS arrow).
 * The arrow points in the direction of [angleDeg], where 0° = pointing UP.
 * Shape: elongated triangle with a concave notch at the base.
 *
 * @param canvas   Canvas to draw on
 * @param cx       Center X of the arrow
 * @param cy       Center Y of the arrow
 * @param size     Half-height of the arrow in pixels
 * @param angleDeg Rotation in degrees clockwise from UP (0 = pointing up = headwind from front)
 * @param color    Arrow fill color (ARGB int)
 */
private fun drawNavigationArrow(canvas: Canvas, cx: Float, cy: Float, size: Float, angleDeg: Double, color: Int) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }

    // Normalized arrow vertices (pointing UP at angle=0):
    // tip at top, wide base below, concave notch in middle of base
    //
    //       (0, -1)        ← tip
    //      /       \
    // (-0.38, 0.55) (0.38, 0.55)  ← wing tips
    //      \       /
    //       (0, 0.28)      ← concave notch (inward)
    //
    val tipY     = -1.00f
    val wingX    =  0.38f
    val wingY    =  0.55f
    val notchY   =  0.28f  // the concave point (less than wingY)

    val radians = Math.toRadians(angleDeg)
    val sinA = sin(radians).toFloat()
    val cosA = cos(radians).toFloat()

    fun rotateX(nx: Float, ny: Float) = cx + size * (nx * cosA - ny * sinA)
    fun rotateY(nx: Float, ny: Float) = cy + size * (nx * sinA + ny * cosA)

    val path = Path().apply {
        moveTo(rotateX(0f, tipY), rotateY(0f, tipY))
        lineTo(rotateX(wingX, wingY), rotateY(wingX, wingY))
        lineTo(rotateX(0f, notchY), rotateY(0f, notchY))
        lineTo(rotateX(-wingX, wingY), rotateY(-wingX, wingY))
        close()
    }
    canvas.drawPath(path, paint)
}

/**
 * Render `text` into an `ARGB_8888` bitmap with the baseline pinned to the
 * bitmap's bottom edge. If [windArrowAngle] is non-null, a navigation arrow
 * is drawn to the left of the text (or above for narrow cells).
 *
 * `bitmap.density = Bitmap.DENSITY_NONE` so RemoteViews renders at native
 * pixel size with no scaling. Width is clamped to `cellWidthPx`.
 */
fun renderValueBitmap(
    text: String,
    fontSizePx: Float,
    bitmapHeightPx: Int,
    cellWidthPx: Float,
    color: Int,
    alignment: ViewConfig.Alignment,
    windArrowAngle: Double? = null,
): Bitmap {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("relative", Typeface.NORMAL)
        textSize = fontSizePx
        this.color = color
        letterSpacing = LETTER_SPACING
        textAlign = Paint.Align.LEFT
    }

    val hasPercent = text.endsWith("%")
    val mainText = if (hasPercent) text.dropLast(1) else text

    val mainWidth = paint.measureText(mainText)
    val suffixWidth = if (hasPercent) {
        paint.textSize = fontSizePx * 0.6f
        val w = paint.measureText("%")
        paint.textSize = fontSizePx
        w
    } else 0f
    val textTotalWidth = mainWidth + suffixWidth

    // Arrow size: ~70% of bitmap height, with a gap to the right
    val arrowSize = if (windArrowAngle != null) bitmapHeightPx * 0.38f else 0f
    val arrowGap  = if (windArrowAngle != null) (bitmapHeightPx * 0.08f).coerceAtLeast(2f) else 0f
    val arrowBlockWidth = if (windArrowAngle != null) arrowSize * 2f + arrowGap else 0f

    val totalWidth = (textTotalWidth + arrowBlockWidth).toInt().coerceIn(1, cellWidthPx.toInt().coerceAtLeast(1))

    // Baseline: use digit "0" as reference so all cells sit on the same baseline
    val bounds = Rect()
    paint.getTextBounds("0", 0, 1, bounds)
    val baselineY = (bitmapHeightPx - bounds.bottom).toFloat()

    val bitmap = Bitmap.createBitmap(totalWidth, bitmapHeightPx, Bitmap.Config.ARGB_8888)
    bitmap.density = Bitmap.DENSITY_NONE
    val canvas = Canvas(bitmap)

    // -- Draw arrow --
    if (windArrowAngle != null) {
        val arrowCx = arrowSize          // center of arrow block
        val arrowCy = bitmapHeightPx / 2f
        drawNavigationArrow(canvas, arrowCx, arrowCy, arrowSize, windArrowAngle, color)
    }

    // -- Draw text --
    paint.textAlign = Paint.Align.LEFT
    paint.textSize = fontSizePx
    val textStartX = arrowBlockWidth

    canvas.drawText(mainText, textStartX, baselineY, paint)
    if (hasPercent) {
        paint.textSize = fontSizePx * 0.6f
        canvas.drawText("%", textStartX + mainWidth, baselineY, paint)
    }

    return bitmap
}
