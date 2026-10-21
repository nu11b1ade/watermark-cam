package com.nu11b1ade.watermarkcam

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import com.nu11b1ade.watermarkcam.domain.Coordinates
import com.nu11b1ade.watermarkcam.domain.OverrideState
import com.nu11b1ade.watermarkcam.domain.PlaceOverride
import com.nu11b1ade.watermarkcam.domain.WatermarkConfig
import com.nu11b1ade.watermarkcam.domain.WatermarkContent
import com.nu11b1ade.watermarkcam.domain.WatermarkSize
import com.nu11b1ade.watermarkcam.domain.composeWatermark
import com.nu11b1ade.watermarkcam.save.WatermarkedJpegWriter
import java.io.File
import java.time.LocalDateTime
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
class WatermarkedJpegWriterTest {
  @get:Rule val temporary = TemporaryFolder()

  @Test fun `hidden watermark fields still write only manual GPS and overridden EXIF timestamps`() {
    val source = temporary.newFile("camera-gps.jpg")
    createCameraJpeg(source)
    ExifInterface(source).apply { setLatLong(31.2, 121.5); saveAttributes() }
    val watermark = composeWatermark(
      WatermarkConfig(
        showTime = false, showPlace = false, showCoordinates = false, showWeather = false, showCustomText = false,
        overrides = OverrideState(LocalDateTime.parse("2025-01-02T03:04:05"), PlaceOverride("手动地点", Coordinates(-33.86, -70.66))),
      ),
      WatermarkContent(ZonedDateTime.parse("2026-10-21T09:08:07+08:00"), "上海", Coordinates(31.2, 121.5)),
    )
    val output = temporary.newFile("manual-gps.jpg")
    WatermarkedJpegWriter().write(source, output, watermark)

    val exif = ExifInterface(output)
    assertEquals(-33.86, exif.latLong!![0], 0.000001)
    assertEquals(-70.66, exif.latLong!![1], 0.000001)
    assertEquals("2025:01:01", exif.getAttribute(ExifInterface.TAG_GPS_DATESTAMP))
    assertEquals("19:04:05", exif.getAttribute(ExifInterface.TAG_GPS_TIMESTAMP))
    listOf(ExifInterface.TAG_DATETIME, ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_DATETIME_DIGITIZED).forEach {
      assertEquals("2025:01:02 03:04:05", exif.getAttribute(it))
    }
    val photo = BitmapFactory.decodeFile(output.path)
    try {
      assertTrue("No rectangle when all watermark fields are hidden", Color.blue(photo.getPixel(30, 330)) > 190)
    } finally {
      photo.recycle()
    }
  }

  @Test fun `rotated camera JPEG becomes upright with visible burn-in and no original GPS leakage`() {
    val source = temporary.newFile("camera.jpg")
    createCameraJpeg(source)
    ExifInterface(source).apply {
      setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
      setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2026:10:21 09:08:07")
      setLatLong(31.2, 121.5)
      saveAttributes()
    }
    val watermark = composeWatermark(
      WatermarkConfig(overrides = OverrideState(
        time = LocalDateTime.parse("2025-01-02T03:04:05"), place = PlaceOverride("家"),
      )),
      WatermarkContent(ZonedDateTime.parse("2026-10-21T09:08:07+08:00"), "上海", Coordinates(31.2, 121.5)),
    )
    val output = temporary.newFile("watermarked.jpg")
    WatermarkedJpegWriter().write(source, output, watermark)

    val photo = BitmapFactory.decodeFile(output.path)
    try {
      assertEquals(360, photo.width)
      assertEquals(480, photo.height)
      assertTrue("Image away from watermark is retained", Color.blue(photo.getPixel(30, 30)) > 190)
      assertTrue("No background panel beside the text", Color.blue(photo.getPixel(300, 440)) > 190)
      assertTrue("Watermark has actual white text pixels", (340 until 466).any { y ->
        (8 until 270).any { x ->
          val pixel = photo.getPixel(x, y)
          Color.red(pixel) > 225 && Color.green(pixel) > 225 && Color.blue(pixel) > 225
        }
      })
    } finally {
      photo.recycle()
    }
    val exif = ExifInterface(output)
    assertEquals("2025:01:02 03:04:05", exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
    assertEquals("+08:00", exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL))
    assertEquals("{\"place\":\"\\u5bb6\"}", exif.getAttribute(ExifInterface.TAG_USER_COMMENT))
    assertNull(exif.latLong)
    assertNull(exif.getAttribute(ExifInterface.TAG_GPS_TIMESTAMP))
    assertEquals(ExifInterface.ORIENTATION_NORMAL, exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0))
  }

  @Test fun `JPEG writer retains the selected watermark size from the prepared config`() {
    val source = temporary.newFile("size-camera.jpg")
    createCameraJpeg(source)
    val heights = listOf(WatermarkSize.MEDIUM, WatermarkSize.XLARGE).map { size ->
      val output = temporary.newFile("${size.name}.jpg")
      val watermark = composeWatermark(
        WatermarkConfig(showPlace = false, showCoordinates = false, showWeather = false, watermarkSize = size),
        WatermarkContent(ZonedDateTime.parse("2026-10-21T09:08:07+08:00")),
      )
      WatermarkedJpegWriter().write(source, output, watermark)
      val photo = requireNotNull(BitmapFactory.decodeFile(output.path))
      try {
        val whiteRows = (0 until photo.height).filter { y ->
          (0 until photo.width).any { x ->
            val pixel = photo.getPixel(x, y)
            Color.red(pixel) > 225 && Color.green(pixel) > 225 && Color.blue(pixel) > 225
          }
        }
        assertTrue("$size writes visible text", whiteRows.isNotEmpty())
        whiteRows.last() - whiteRows.first() + 1
      } finally {
        photo.recycle()
      }
    }
    assertTrue("Extra large stays visibly larger after JPEG encoding: $heights", heights[1] > heights[0] * 1.4)
  }

  private fun createCameraJpeg(file: File) {
    val bitmap = Bitmap.createBitmap(480, 360, Bitmap.Config.ARGB_8888)
    try {
      bitmap.eraseColor(Color.rgb(90, 180, 220))
      file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)) }
    } finally {
      bitmap.recycle()
    }
  }
}
