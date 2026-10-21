package com.nu11b1ade.watermarkcam.watermark

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.graphics.withTranslation

/** The preview uses the same 360-wide relative spacing and 14-line limit. */
object WatermarkStyle {
  const val BASE_WIDTH = 360f
  const val MARGIN = 12f
  const val PADDING = 10f
  const val FONT_SIZE = 12f
  const val LINE_HEIGHT = 17f
  const val CORNER_RADIUS = 8f
  const val MAX_LINES = 14
}

class BitmapWatermarkBurner {
  /** Returns a mutable bitmap, reusing [source] when possible. Empty lines leave its pixels intact. */
  fun burn(source: Bitmap, lines: List<String>): Bitmap {
    val bitmap = if (source.isMutable) source else checkNotNull(source.copy(Bitmap.Config.ARGB_8888, true))
    if (lines.isEmpty()) return bitmap
    val scale = bitmap.width / WatermarkStyle.BASE_WIDTH
    val margin = WatermarkStyle.MARGIN * scale
    val padding = WatermarkStyle.PADDING * scale
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.WHITE
      textSize = WatermarkStyle.FONT_SIZE * scale
      typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    val width = (bitmap.width - 2 * margin - 2 * padding).toInt().coerceAtLeast(1)
    val text = lines.joinToString("\n")
    val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
      .setAlignment(Layout.Alignment.ALIGN_NORMAL)
      .setIncludePad(false)
      .setLineSpacing((WatermarkStyle.LINE_HEIGHT * scale - paint.fontSpacing).coerceAtLeast(0f), 1f)
      .setMaxLines(WatermarkStyle.MAX_LINES)
      .setEllipsize(TextUtils.TruncateAt.END)
      .build()
    val height = layout.height + 2 * padding
    val top = (bitmap.height - margin - height).coerceAtLeast(margin)
    val canvas = Canvas(bitmap)
    canvas.drawRoundRect(
      margin, top, bitmap.width - margin, top + height,
      WatermarkStyle.CORNER_RADIUS * scale, WatermarkStyle.CORNER_RADIUS * scale,
      Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(166, 0, 0, 0) },
    )
    canvas.withTranslation(margin + padding, top + padding) { layout.draw(this) }
    return bitmap
  }
}
