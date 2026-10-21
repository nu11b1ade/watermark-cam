package com.nu11b1ade.watermarkcam

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.nu11b1ade.watermarkcam.domain.PhotoFrame
import com.nu11b1ade.watermarkcam.domain.WatermarkConfig
import com.nu11b1ade.watermarkcam.domain.WatermarkContent
import com.nu11b1ade.watermarkcam.domain.composeWatermark
import com.nu11b1ade.watermarkcam.save.WatermarkedJpegWriter
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoFrameWriterTest {
  @get:Rule val temporary = TemporaryFolder()

  @Test fun `JPEG excludes the area hidden by controls before anchoring the watermark at its bottom`() {
    val source = temporary.newFile("full-preview.jpg")
    val bitmap = Bitmap.createBitmap(480, 640, Bitmap.Config.ARGB_8888)
    try {
      bitmap.eraseColor(Color.rgb(90, 180, 220))
      Canvas(bitmap).drawRect(0f, 480f, 480f, 640f, Paint().apply { color = Color.MAGENTA })
      source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }
    } finally {
      bitmap.recycle()
    }
    val watermark = composeWatermark(WatermarkConfig(), WatermarkContent(
      ZonedDateTime.parse("2026-10-21T09:08:07+08:00"), "公园南门",
    )).copy(frame = PhotoFrame.aboveControls(previewHeight = 640, controlsHeight = 160))
    val destination = temporary.newFile("visible-photo.jpg")
    WatermarkedJpegWriter().write(source, destination, watermark)
    val photo = requireNotNull(BitmapFactory.decodeFile(destination.path))
    try {
      assertEquals(480, photo.width)
      assertEquals(480, photo.height)
      assertTrue("Visible scene is retained rather than the magenta area underneath the controls",
        Color.green(photo.getPixel(470, 470)) > 150)
      assertTrue("No background panel beside the text", Color.blue(photo.getPixel(360, 450)) > 190)
      assertTrue("White watermark text sits at the visible frame's bottom-left", (430 until 466).any { y ->
        (16 until 120).any { x ->
          val pixel = photo.getPixel(x, y)
          Color.red(pixel) > 225 && Color.green(pixel) > 225 && Color.blue(pixel) > 225
        }
      })
    } finally {
      photo.recycle()
    }
  }
}
