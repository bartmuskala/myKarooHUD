package com.bartmuskala.mykaroohud.datatype.shared

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Typeface
import io.hammerhead.karooext.models.ViewConfig
import kotlin.math.cos
import kotlin.math.sin

private const val MIN_BITMAP_HEIGHT_PX = 30
private const val LETTER_SPACING = -0.04f

fun valueBitmapHeightPx(valueFontBaseSp: Int, density: Float): Int {
    val raw = (0.74f * valueFontBaseSp * density).toInt()
    return raw.coerceAtLeast(MIN_BITMAP_HEIGHT_PX)
}

/**
 * Draws a navigation-style arrow (compass needle shape) on [canvas].
 * [angleDeg] = 0 → arrow points UP; increases clockwise.
 */
private fun drawNavigationArrow(
    canvas: Canvas,
    cx: Float,
    cy: Float,
    size: Float,
    angleDeg: Double,
    color: Int,
) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }
    val radians = Math.toRadians(angleDeg)
    val sinA = sin(radians).toFloat()
    val cosA = cos(radians).toFloat()

    // Arrow shape (normalized, pointing up at angle 0):
    //   tip (0, -1), wings (±0.38, +0.55), concave notch (0, +0.28)
    fun rx(nx: Float, ny: Float) = cx + size * (nx * cosA - ny * sinA)
    fun ry(nx: Float, ny: Float) = cy + size * (nx * sinA + ny * cosA)

    val path = Path().apply {
        moveTo(rx(0f, -1.00f), ry(0f, -1.00f))
        lineTo(rx(0.38f, 0.55f), ry(0.38f, 0.55f))
        lineTo(rx(0f, 0.28f), ry(0f, 0.28f))
        lineTo(rx(-0.38f, 0.55f), ry(-0.38f, 0.55f))
        close()
    }
    canvas.drawPath(path, paint)
}

/**
 * Render [text] into an ARGB_8888 bitmap.
 *
 * When [windArrowAngle] is non-null the arrow is drawn to the LEFT of the text
 * and the font size is derived from a cell width reduced by the arrow block,
 * keeping the number visually the same size as adjacent power/W'prime fields.
 *
 * Arrow size is fixed at 55 % of [bitmapHeightPx] (half-size radius), vertically
 * centred in the bitmap so its centre matches the vertical centre of the digits.
 *
 * bitmap.density = Bitmap.DENSITY_NONE → rendered at native pixel size.
 */
fun renderValueBitmap(
    text: String,
    fontSizePx: Float,           // pre-computed by fontSizeForCell for the FULL cell
    bitmapHeightPx: Int,
    cellWidthPx: Float,
    color: Int,
    alignment: ViewConfig.Alignment,
    windArrowAngle: Double? = null,
): Bitmap {
    // ---- Arrow geometry -------------------------------------------------------
    // radius = 45 % of bitmap height keeps the arrow comfortably inside the cell
    val arrowRadius = if (windArrowAngle != null) bitmapHeightPx * 0.45f else 0f
    val arrowDiam   = arrowRadius * 2f
    val arrowGap    = if (windArrowAngle != null) (bitmapHeightPx * 0.10f).coerceAtLeast(3f) else 0f
    val arrowBlock  = arrowDiam + arrowGap // total horizontal space reserved for arrow

    // ---- Font size for the text part ------------------------------------------
    // Re-measure with a paint that matches the actual typeface so that a 2-char
    // wind value ("14") renders at the same visual size as "250" (3-char power).
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("relative", Typeface.NORMAL)
        textSize = fontSizePx
        this.color = color
        letterSpacing = LETTER_SPACING
        textAlign = Paint.Align.LEFT
    }

    // Scale font down if text would overflow the remaining width after the arrow block
    val hasPercent = text.endsWith("%")
    val mainText   = if (hasPercent) text.dropLast(1) else text

    // Always render the number at the full base font size.
    // The arrow sits to the left and does not reduce the font size.
    val scaledFontPx = fontSizePx

    paint.textSize = scaledFontPx
    val mainWidth   = paint.measureText(mainText)
    val suffixWidth = if (hasPercent) {
        paint.textSize = scaledFontPx * 0.6f
        val w = paint.measureText("%")
        paint.textSize = scaledFontPx
        w
    } else 0f
    val textTotalWidth = mainWidth + suffixWidth

    // ---- Baseline (stable across all cells) -----------------------------------
    val bounds = Rect()
    paint.textSize = scaledFontPx
    paint.getTextBounds("0", 0, 1, bounds)
    val baselineY = (bitmapHeightPx - bounds.bottom).toFloat()

    // ---- Bitmap ---------------------------------------------------------------
    val totalWidth = (arrowBlock + textTotalWidth)
        .toInt().coerceIn(1, cellWidthPx.toInt().coerceAtLeast(1))

    val bitmap = Bitmap.createBitmap(totalWidth, bitmapHeightPx, Bitmap.Config.ARGB_8888)
    bitmap.density = Bitmap.DENSITY_NONE
    val canvas = Canvas(bitmap)

    // Arrow: centred vertically in the bitmap, horizontally within arrowBlock
    if (windArrowAngle != null) {
        val arrowCx = arrowRadius               // centre of the arrowDiam block
        val arrowCy = bitmapHeightPx / 2f
        drawNavigationArrow(canvas, arrowCx, arrowCy, arrowRadius, windArrowAngle, color)
    }

    // Text: starts right after the arrow block
    paint.textAlign = Paint.Align.LEFT
    paint.textSize  = scaledFontPx
    canvas.drawText(mainText, arrowBlock, baselineY, paint)
    if (hasPercent) {
        paint.textSize = scaledFontPx * 0.6f
        canvas.drawText("%", arrowBlock + mainWidth, baselineY, paint)
    }

    return bitmap
}
