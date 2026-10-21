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
  const val MARGIN = 16f
  const val CARD_WIDTH = 328f
  const val PADDING = 14f
  const val GAP = 8f
  const val TIME_SIZE = 36f
  const val DETAIL_SIZE = 15f
  const val CORNER_RADIUS = 14f
}

/** One canvas design for both CameraX's live overlay and full-resolution saved pixels. */
class WatermarkCardRenderer(private val card: WatermarkCard) {
  private val clockPaint = textPaint(WatermarkStyle.TIME_SIZE, bold = true)
  private val datePaint = textPaint(14f)
  private val detailPaint = textPaint(WatermarkStyle.DETAIL_SIZE)
  private val tagPaint = textPaint(13f, bold = true).apply { color = Color.rgb(36, 18, 5) }
  private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = WatermarkStyle.ACCENT_ARGB }
  private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(140, 0, 0, 0) }
  private val textWidth = (WatermarkStyle.CARD_WIDTH - 2 * WatermarkStyle.PADDING).toInt()
  private val tag = card.tag?.let { layout(it, tagPaint, textWidth - 20, 1) }
  private val rows = listOfNotNull(card.place, card.coordinates, card.weather).map {
    layout(it, detailPaint, textWidth - 16, 2)
  }

  fun draw(canvas: Canvas, width: Float, height: Float) {
    val blocks = rows.size + (if (tag != null) 1 else 0) + (if (card.time != null) 1 else 0)
    if (blocks == 0 || width <= 0f || height <= 0f) return
    val scale = minOf(width, height) / WatermarkStyle.BASE_SHORT_EDGE
    val tagHeight = if (tag != null) 26f else 0f
    val timeHeight = if (card.time != null) 44f else 0f
    val cardHeight = 2 * WatermarkStyle.PADDING + tagHeight + timeHeight +
      rows.sumOf { it.height } + (blocks - 1) * WatermarkStyle.GAP

    canvas.withSave {
      scale(scale, scale)
      translate(WatermarkStyle.MARGIN, height / scale - WatermarkStyle.MARGIN - cardHeight)
      drawRoundRect(0f, 0f, WatermarkStyle.CARD_WIDTH, cardHeight,
        WatermarkStyle.CORNER_RADIUS, WatermarkStyle.CORNER_RADIUS, backgroundPaint)
      var y = WatermarkStyle.PADDING
      tag?.let {
        val chipWidth = it.getLineWidth(0) + 20f
        drawRoundRect(WatermarkStyle.PADDING, y, WatermarkStyle.PADDING + chipWidth, y + tagHeight,
          7f, 7f, accentPaint)
        withTranslation(WatermarkStyle.PADDING + 10f, y + (tagHeight - it.height) / 2f) { it.draw(this) }
        y += tagHeight + WatermarkStyle.GAP
      }
      card.time?.let {
        drawText(it.clock, WatermarkStyle.PADDING, y - clockPaint.fontMetrics.ascent, clockPaint)
        val dividerX = WatermarkStyle.PADDING + clockPaint.measureText(it.clock) + 12f
        drawRoundRect(dividerX, y + 4f, dividerX + 3f, y + 40f, 1.5f, 1.5f, accentPaint)
        val dateX = dividerX + 15f
        drawText(it.date, dateX, y + 3f - datePaint.fontMetrics.ascent, datePaint)
        drawText(it.weekday, dateX, y + 24f - datePaint.fontMetrics.ascent, datePaint)
        y += timeHeight + WatermarkStyle.GAP
      }
      rows.forEach {
        drawCircle(WatermarkStyle.PADDING + 3f, y + WatermarkStyle.DETAIL_SIZE * 0.65f, 2.2f, accentPaint)
        withTranslation(WatermarkStyle.PADDING + 16f, y) { it.draw(this) }
        y += it.height + WatermarkStyle.GAP
      }
    }
  }

  private fun layout(text: String, paint: TextPaint, width: Int, maxLines: Int): StaticLayout =
    StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
      .setAlignment(Layout.Alignment.ALIGN_NORMAL)
      .setIncludePad(false)
      .setLineSpacing(3f, 1f)
      .setMaxLines(maxLines)
      .setEllipsize(TextUtils.TruncateAt.END)
      .build()

  private fun textPaint(size: Float, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.WHITE
    textSize = size
    typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
  }
}
