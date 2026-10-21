package com.nu11b1ade.watermarkcam.watermark

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.graphics.withSave
import androidx.core.graphics.withTranslation
import com.nu11b1ade.watermarkcam.domain.WatermarkCard

object WatermarkStyle {
  const val ACCENT_ARGB = 0xFFFF7A1A.toInt()
  const val BASE_SHORT_EDGE = 360f
  const val MARGIN_RATIO = 0.04f
  const val TEXT_WIDTH_RATIO = 0.70f
  const val MAX_TEXT_WIDTH_RATIO = 0.92f
  const val GAP = 6f
  const val TIME_SIZE = 26f
  const val DATE_SIZE = 11f
  const val DETAIL_SIZE = 11.5f
}

/** One canvas design for both CameraX's live overlay and full-resolution saved pixels. */
class WatermarkCardRenderer(private val card: WatermarkCard) {
  private val clockPaint = textPaint(WatermarkStyle.TIME_SIZE, bold = true)
  private val datePaint = textPaint(WatermarkStyle.DATE_SIZE)
  private val detailPaint = textPaint(WatermarkStyle.DETAIL_SIZE)
  private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = WatermarkStyle.ACCENT_ARGB
    strokeWidth = 1.25f
    strokeCap = Paint.Cap.ROUND
  }
  private val sizeScale = card.watermarkSize.scale
  // Keep larger text inside the same four-percent frame margins, including the XLARGE size.
  private val textWidth = (WatermarkStyle.BASE_SHORT_EDGE *
    minOf(WatermarkStyle.TEXT_WIDTH_RATIO * sizeScale, WatermarkStyle.MAX_TEXT_WIDTH_RATIO) / sizeScale).toInt()
  private val dividerX = (card.time?.let { clockPaint.measureText(it.clock) } ?: 0f) + 8f
  private val dateX = dividerX + 9f
  private val date = card.time?.let { layout("${it.date}\n${it.weekday}", datePaint, textWidth - dateX.toInt(), 2) }
  private val timeHeight = date?.let { maxOf(clockPaint.fontSpacing, it.height.toFloat()) } ?: 0f
  private val rows = listOfNotNull(card.place, card.coordinates, card.weather, card.tag).map {
    layout(it, detailPaint, textWidth, 2)
  }

  fun draw(canvas: Canvas, width: Float, height: Float) {
    val blocks = rows.size + (if (card.time != null) 1 else 0)
    if (blocks == 0 || width <= 0f || height <= 0f) return
    val shortEdge = minOf(width, height)
    val scale = shortEdge / WatermarkStyle.BASE_SHORT_EDGE * sizeScale
    val margin = shortEdge * WatermarkStyle.MARGIN_RATIO
    val contentHeight = timeHeight + rows.sumOf { it.height } + (blocks - 1) * WatermarkStyle.GAP

    canvas.withSave {
      translate(margin, height - margin - contentHeight * scale)
      scale(scale, scale)
      var y = 0f
      card.time?.let {
        val metrics = clockPaint.fontMetrics
        val clockTop = y + (timeHeight - (metrics.descent - metrics.ascent)) / 2f
        drawText(it.clock, 0f, clockTop - metrics.ascent, clockPaint)
        drawLine(dividerX, y + 2f, dividerX, y + timeHeight - 2f, accentPaint)
        date?.let { label ->
          withTranslation(dateX, y + (timeHeight - label.height) / 2f) { label.draw(this) }
        }
        y += timeHeight + WatermarkStyle.GAP
      }
      rows.forEach {
        withTranslation(0f, y) { it.draw(this) }
        y += it.height + WatermarkStyle.GAP
      }
    }
  }

  private fun layout(text: String, paint: TextPaint, width: Int, maxLines: Int): StaticLayout =
    StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
      .setAlignment(Layout.Alignment.ALIGN_NORMAL)
      .setIncludePad(false)
      .setLineSpacing(2f, 1f)
      .setMaxLines(maxLines)
      .setEllipsize(TextUtils.TruncateAt.END)
      .build()

  private fun textPaint(size: Float, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.WHITE
    textSize = size
    typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
    setShadowLayer(3f, 0f, 1f, Color.argb(160, 0, 0, 0))
  }
}
