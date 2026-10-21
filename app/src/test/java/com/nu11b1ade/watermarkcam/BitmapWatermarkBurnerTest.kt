package com.nu11b1ade.watermarkcam

import android.graphics.Bitmap
import android.graphics.Color
import com.nu11b1ade.watermarkcam.domain.Coordinates
import com.nu11b1ade.watermarkcam.domain.WatermarkCard
import com.nu11b1ade.watermarkcam.domain.WatermarkConfig
import com.nu11b1ade.watermarkcam.domain.WatermarkContent
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
  @Test fun `landscape card changes the existing saved-photo check area and preserves the scene`() {
    val bitmap = Bitmap.createBitmap(1280, 960, Bitmap.Config.ARGB_8888)
    val scene = Color.rgb(90, 180, 220)
    bitmap.eraseColor(scene)
    try {
      val card = composeWatermark(WatermarkConfig(), WatermarkContent(
        ZonedDateTime.parse("2026-10-21T09:08:07+08:00"), "上海市黄浦区延安东路", Coordinates(31.2304, 121.4737),
      )).card
      BitmapWatermarkBurner().burn(bitmap, card)

      assertEquals(scene, bitmap.getPixel(640, 120))
      val changed = (800 until 880).sumOf { y ->
        (100 until 900).count { x -> Color.blue(bitmap.getPixel(x, y)) < 180 }
      }
      assertTrue("Card must remain clearly burned into the lower-left area", changed > 32_000)
    } finally {
      bitmap.recycle()
    }
  }

  @Test fun `card keeps short-edge proportions on twelve megapixel portrait and landscape photos`() {
    val card = composeWatermark(WatermarkConfig(), WatermarkContent(
      ZonedDateTime.parse("2026-10-21T09:08:07+08:00"), "上海市黄浦区", Coordinates(31.2304, 121.4737),
    )).card
    listOf(360 to 480, 3000 to 4000, 4000 to 3000).forEach { (width, height) ->
      val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
      bitmap.eraseColor(Color.rgb(90, 180, 220))
      try {
        BitmapWatermarkBurner().burn(bitmap, card)
        val scale = minOf(width, height) / 360f
        assertTrue(Color.blue(bitmap.getPixel((30 * scale).toInt(), height - (20 * scale).toInt())) < 140)
        assertEquals("Margin remains outside the card", Color.rgb(90, 180, 220),
          bitmap.getPixel((8 * scale).toInt(), height - (20 * scale).toInt()))
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
      assertEquals("Content stays in the lower-left card", Color.CYAN, bitmap.getPixel(18, 1))
    } finally {
      bitmap.recycle()
    }
  }
}
