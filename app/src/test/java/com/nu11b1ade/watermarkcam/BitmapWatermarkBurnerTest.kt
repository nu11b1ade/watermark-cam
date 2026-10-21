package com.nu11b1ade.watermarkcam

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import com.nu11b1ade.watermarkcam.domain.Coordinates
import com.nu11b1ade.watermarkcam.domain.WatermarkCard
import com.nu11b1ade.watermarkcam.domain.WatermarkConfig
import com.nu11b1ade.watermarkcam.domain.WatermarkContent
import com.nu11b1ade.watermarkcam.domain.WatermarkSize
import com.nu11b1ade.watermarkcam.domain.composeWatermark
import com.nu11b1ade.watermarkcam.watermark.BitmapWatermarkBurner
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BitmapWatermarkBurnerTest {
  private val scene = Color.rgb(90, 180, 220)
  private val observed = WatermarkContent(
    ZonedDateTime.parse("2026-10-21T09:08:07+08:00"), "上海市黄浦区延安东路", Coordinates(31.2304, 121.4737),
  )

  @Test fun `text burns into the lower left while its gaps and the scene keep their original pixels`() {
    val bitmap = Bitmap.createBitmap(1280, 960, Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(scene)
    try {
      BitmapWatermarkBurner().burn(bitmap, composeWatermark(WatermarkConfig(), observed).card)

      assertEquals(scene, bitmap.getPixel(640, 120))
      assertEquals("No card behind the text", scene, bitmap.getPixel(720, 880))
      val bounds = changedBounds(bitmap)
      val unchanged = (bounds.top until bounds.bottom).sumOf { y ->
        (bounds.left until bounds.right).count { x -> bitmap.getPixel(x, y) == scene }
      }
      assertTrue("Gaps remain transparent: $unchanged pixels in $bounds", unchanged > bounds.width() * bounds.height() * 0.25)
      assertTrue("The watermark includes white text", (bounds.top until bounds.bottom).any { y ->
        (bounds.left until bounds.right).any { x -> bitmap.getPixel(x, y) == Color.WHITE }
      })
    } finally {
      bitmap.recycle()
    }
  }

  @Test fun `compact watermark keeps short-edge proportions and four percent margins on portrait and landscape photos`() {
    val card = composeWatermark(WatermarkConfig(), observed).card
    var referenceHeight: Float? = null
    listOf(360 to 480, 3000 to 4000, 4000 to 3000).forEach { (width, height) ->
      val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
      bitmap.eraseColor(scene)
      try {
        BitmapWatermarkBurner().burn(bitmap, card)
        val shortEdge = minOf(width, height).toFloat()
        val bounds = changedBounds(bitmap)
        assertTrue("Left margin includes only a small shadow overhang", bounds.left / shortEdge in 0.015f..0.045f)
        assertTrue("Bottom margin stays near four percent", (height - bounds.bottom) / shortEdge in 0.015f..0.05f)
        assertTrue("Medium watermark stays compact", bounds.height() / shortEdge < 0.34f)
        assertTrue("Text width is capped near seventy percent", bounds.width() / shortEdge < 0.75f)
        val normalizedHeight = bounds.height() * 360f / shortEdge
        referenceHeight?.let { assertEquals(it, normalizedHeight, 2f) }
        referenceHeight = normalizedHeight
      } finally {
        bitmap.recycle()
      }
    }
  }

  @Test fun `each larger size increases the burned-in height without changing the anchor margin`() {
    val heights = WatermarkSize.entries.map { size ->
      val bitmap = Bitmap.createBitmap(720, 960, Bitmap.Config.ARGB_8888)
      bitmap.eraseColor(scene)
      try {
        val card = composeWatermark(WatermarkConfig(watermarkSize = size), observed).card
        BitmapWatermarkBurner().burn(bitmap, card)
        val bounds = changedBounds(bitmap)
        assertTrue("$size stays above the bottom margin", 960 - bounds.bottom in 12..36)
        bounds.height()
      } finally {
        bitmap.recycle()
      }
    }
    assertTrue("Size heights must strictly increase: $heights", heights.zipWithNext().all { (a, b) -> a < b })
  }

  @Test fun `custom text is white with readable dark shadows and no colored label block`() {
    val bitmap = Bitmap.createBitmap(360, 480, Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(Color.WHITE)
    try {
      BitmapWatermarkBurner().burn(bitmap, WatermarkCard(tag = "平安喜乐"))
      val pixels = IntArray(bitmap.width * bitmap.height)
      bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

      assertTrue("Shadows keep white text readable on a bright scene", pixels.any { Color.red(it) < 230 })
      assertTrue("Custom text has no orange chip", pixels.all { Color.red(it) == Color.green(it) && Color.green(it) == Color.blue(it) })
      assertTrue("The lower-left text area is mostly untouched", (440 until 466).sumOf { y ->
        (14 until 100).count { x -> bitmap.getPixel(x, y) == Color.WHITE }
      } > 1_200)
    } finally {
      bitmap.recycle()
    }
  }

  @Test fun `long text wraps to at most two lines and extra large stays within the photo`() {
    listOf(WatermarkSize.MEDIUM, WatermarkSize.XLARGE).forEach { size ->
      val bitmap = Bitmap.createBitmap(720, 960, Bitmap.Config.ARGB_8888)
      bitmap.eraseColor(scene)
      try {
        val config = WatermarkConfig(showTime = false, showCoordinates = false, showWeather = false, watermarkSize = size)
        BitmapWatermarkBurner().burn(bitmap, composeWatermark(config, observed.copy(place = "很长的地点".repeat(24))).card)
        val bounds = changedBounds(bitmap)

        assertTrue("$size has at most two text lines plus shadows", bounds.height() < 45 * 2 * size.scale)
        assertTrue("$size leaves room for margins and shadow", bounds.right < 720 * 0.99)
        assertTrue("Text and shadows never exceed the width cap", bounds.width() < 720 * 0.96)
      } finally {
        bitmap.recycle()
      }
    }
  }

  @Test fun `missing optional fields and long labels are safe even on a tiny bitmap`() {
    val bitmap = Bitmap.createBitmap(36, 48, Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(Color.CYAN)
    try {
      BitmapWatermarkBurner().burn(bitmap, WatermarkCard())
      assertTrue("No background when every field is hidden", (0 until 48).all { y ->
        (0 until 36).all { x -> bitmap.getPixel(x, y) == Color.CYAN }
      })
      BitmapWatermarkBurner().burn(bitmap, WatermarkCard(place = "很长的地点".repeat(24), tag = "今日打卡".repeat(30)))
      assertEquals("Content stays in the lower-left watermark", Color.CYAN, bitmap.getPixel(18, 1))
    } finally {
      bitmap.recycle()
    }
  }

  private fun changedBounds(bitmap: Bitmap): Rect {
    val bounds = Rect(bitmap.width, bitmap.height, 0, 0)
    val row = IntArray(bitmap.width)
    for (y in 0 until bitmap.height) {
      bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
      row.forEachIndexed { x, pixel ->
        if (pixel != scene) {
          bounds.left = minOf(bounds.left, x)
          bounds.top = minOf(bounds.top, y)
          bounds.right = maxOf(bounds.right, x + 1)
          bounds.bottom = maxOf(bounds.bottom, y + 1)
        }
      }
    }
    assertTrue("Visible text must change pixels", !bounds.isEmpty)
    return bounds
  }
}
