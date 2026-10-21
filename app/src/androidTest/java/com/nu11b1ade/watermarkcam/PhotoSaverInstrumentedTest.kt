package com.nu11b1ade.watermarkcam

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.nu11b1ade.watermarkcam.domain.Coordinates
import com.nu11b1ade.watermarkcam.domain.OverrideState
import com.nu11b1ade.watermarkcam.domain.PlaceOverride
import com.nu11b1ade.watermarkcam.domain.WatermarkConfig
import com.nu11b1ade.watermarkcam.domain.WatermarkContent
import com.nu11b1ade.watermarkcam.domain.composeWatermark
import com.nu11b1ade.watermarkcam.save.PhotoSaver
import java.io.File
import java.time.LocalDateTime
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class PhotoSaverInstrumentedTest {
  @Test fun savedJpegHasOverriddenExifAndBurnedWatermark() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val raw = File.createTempFile("save-test_", ".jpg", context.cacheDir)
    var saved: Uri? = null
    try {
      createGradientJpeg(raw)
      val watermark = composeWatermark(
        WatermarkConfig(overrides = OverrideState(
          time = LocalDateTime.parse("2024-05-06T07:08:09"),
          place = PlaceOverride("上海市黄浦区延安东路", Coordinates(31.2304, 121.4737)),
        )),
        WatermarkContent(ZonedDateTime.parse("2026-10-21T11:00:00+08:00"), "北京", Coordinates(39.9042, 116.4074)),
      )

      // Exercises the same real JPEG writer and MediaStore transaction as CameraX capture.
      val uri = PhotoSaver(context).saveCaptured(raw, watermark)
      saved = uri
      val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
      assertTrue("JPEG start-of-image marker", bytes.size > 2 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte())
      val options = BitmapFactory.Options()
      val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
      assertNotNull("Saved Uri must open as a decodable JPEG", decoded)
      assertEquals("image/jpeg", options.outMimeType)
      val photo = requireNotNull(decoded)
      val source = requireNotNull(BitmapFactory.decodeFile(raw.path))
      try {
        assertEquals(1280, photo.width)
        assertEquals(960, photo.height)
        // Ignore small JPEG re-encoding noise: the lower-left watermark must change many pixels substantially.
        val changed = (800 until 880).sumOf { y ->
          (100 until 900).count { x -> colorDifference(source.getPixel(x, y), photo.getPixel(x, y)) > 40 }
        }
        assertTrue("Watermark is burned into the saved pixels", changed > 32_000)
        assertTrue("Pixels away from watermark retain the source image",
          colorDifference(source.getPixel(640, 120), photo.getPixel(640, 120)) < 15)
      } finally {
        source.recycle()
        photo.recycle()
      }
      val exif = bytes.inputStream().use { ExifInterface(it) }
      assertEquals("2024:05:06 07:08:09", exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
      assertEquals("+08:00", exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL))
      val coordinates = exif.latLong
      assertNotNull("Overridden GPS must be present", coordinates)
      assertEquals(31.2304, coordinates!![0], 1e-4)
      assertEquals(121.4737, coordinates[1], 1e-4)
    } finally {
      try {
        saved?.let { context.contentResolver.delete(it, null, null) }
      } finally {
        raw.delete()
      }
    }
  }

  private fun createGradientJpeg(file: File) {
    val bitmap = Bitmap.createBitmap(1280, 960, Bitmap.Config.ARGB_8888)
    try {
      val paint = Paint().apply {
        shader = LinearGradient(0f, 0f, 1280f, 960f,
          Color.rgb(90, 180, 220), Color.rgb(200, 220, 180), Shader.TileMode.CLAMP)
      }
      Canvas(bitmap).drawRect(0f, 0f, 1280f, 960f, paint)
      file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)) }
    } finally {
      bitmap.recycle()
    }
  }

  private fun colorDifference(first: Int, second: Int): Int = maxOf(
    abs(Color.red(first) - Color.red(second)), abs(Color.green(first) - Color.green(second)),
    abs(Color.blue(first) - Color.blue(second)),
  )
}
