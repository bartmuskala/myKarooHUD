package com.bartmuskala.mykaroohud.datatype.shared

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import io.hammerhead.karooext.models.ViewConfig

private const val MIN_BITMAP_HEIGHT_PX = 30
private const val LETTER_SPACING = -0.04f

/**
 * Constant bitmap height per layout: `0.74 × valueFontBaseSp × density`.
 *
 * Sized to the visible cap (~0.7 × textSize) plus a small buffer; tight
 * enough to fit 5×1's baseline_box without ImageView fitCenter downscaling.
 * Independent of `fontSizeForCell` shrinks so the baseline stays stable
 * across content-driven font changes.
 */
fun valueBitmapHeightPx(valueFontBaseSp: Int, density: Float): Int {
    val raw = (0.74f * valueFontBaseSp * density).toInt()
    return raw.coerceAtLeast(MIN_BITMAP_HEIGHT_PX)
}

/**
 * Render `text` into an `ARGB_8888` bitmap with the baseline pinned to the
 * bitmap's bottom edge (`bounds.bottom` ≈ 0 for digits).
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
): Bitmap {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("relative", Typeface.NORMAL)
        textSize = fontSizePx
        this.color = color
        letterSpacing = LETTER_SPACING
        textAlign = when (alignment) {
            ViewConfig.Alignment.LEFT -> Paint.Align.LEFT
            ViewConfig.Alignment.CENTER -> Paint.Align.CENTER
            ViewConfig.Alignment.RIGHT -> Paint.Align.RIGHT
        }
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
    
    val measuredWidth = mainWidth + suffixWidth
    val cellW = cellWidthPx.toInt().coerceAtLeast(1)
    val width = measuredWidth.toInt().coerceIn(1, cellW)

    val bounds = Rect()
    // Use a fixed reference glyph ("0") for baseline computation so all fields
    // sit on the same baseline regardless of content (arrows, %, digits, etc.)
    paint.getTextBounds("0", 0, 1, bounds)
    val baselineY = (bitmapHeightPx - bounds.bottom).toFloat()

    val bitmap = Bitmap.createBitmap(width, bitmapHeightPx, Bitmap.Config.ARGB_8888)
    bitmap.density = Bitmap.DENSITY_NONE
    val canvas = Canvas(bitmap)

    val xPos = when (alignment) {
        ViewConfig.Alignment.LEFT -> 0f
        ViewConfig.Alignment.CENTER -> width / 2f
        ViewConfig.Alignment.RIGHT -> width.toFloat()
    }
    
    if (alignment == ViewConfig.Alignment.RIGHT) {
        if (hasPercent) {
            paint.textAlign = Paint.Align.RIGHT
            paint.textSize = fontSizePx * 0.6f
            canvas.drawText("%", width.toFloat(), baselineY, paint)
            paint.textSize = fontSizePx
            canvas.drawText(mainText, width - suffixWidth, baselineY, paint)
        } else {
            canvas.drawText(mainText, width.toFloat(), baselineY, paint)
        }
    } else if (alignment == ViewConfig.Alignment.CENTER) {
        paint.textAlign = Paint.Align.LEFT
        val startX = (width - measuredWidth) / 2f
        canvas.drawText(mainText, startX, baselineY, paint)
        if (hasPercent) {
            paint.textSize = fontSizePx * 0.6f
            canvas.drawText("%", startX + mainWidth, baselineY, paint)
            paint.textSize = fontSizePx
        }
    } else {
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText(mainText, 0f, baselineY, paint)
        if (hasPercent) {
            paint.textSize = fontSizePx * 0.6f
            canvas.drawText("%", mainWidth, baselineY, paint)
            paint.textSize = fontSizePx
        }
    }
    return bitmap
}
