package com.nu11b1ade.watermarkcam

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nu11b1ade.watermarkcam.domain.Coordinates
import com.nu11b1ade.watermarkcam.domain.WatermarkConfig
import com.nu11b1ade.watermarkcam.domain.WatermarkContent
import com.nu11b1ade.watermarkcam.domain.WeatherReading
import com.nu11b1ade.watermarkcam.domain.composeWatermark
import com.nu11b1ade.watermarkcam.watermark.BitmapWatermarkBurner
import java.io.File
import java.time.ZonedDateTime
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WatermarkSampleInstrumentedTest {
  @Test fun burnsWatermarkIntoPortraitSceneAndWritesSampleJpeg() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val source = createScene()
    var burned: Bitmap? = null
    try {
      val coordinates = Coordinates(31.2304, 121.4737)
      val watermark = composeWatermark(
        WatermarkConfig(),
        WatermarkContent(
          capturedAt = ZonedDateTime.parse("2026-10-21T08:58:00+08:00"),
          place = "上海市黄浦区人民大道200号",
          coordinates = coordinates,
          weather = WeatherReading(coordinates, 18.0, "晴"),
        ),
      )

      val sampleWidth = source.width / 2
      val sampleHeight = source.height / 4
      val sampleTop = source.height - sampleHeight
      // The burner can reuse the source, so snapshot the lower-left pixels before burning.
      val before = IntArray(sampleWidth * sampleHeight)
      source.getPixels(before, 0, sampleWidth, 0, sampleTop, sampleWidth, sampleHeight)

      val sample = BitmapWatermarkBurner().burn(source, watermark.card)
      burned = sample
      val after = IntArray(before.size)
      sample.getPixels(after, 0, sampleWidth, 0, sampleTop, sampleWidth, sampleHeight)
      val changedPixels = before.indices.count { before[it] != after[it] }
      assertTrue("Watermark must visibly change the bottom-left area", changedPixels > before.size / 10)

      val directory = checkNotNull(context.getExternalFilesDir(null)) {
        "Target app external files directory is unavailable"
      }
      File(directory, "watermark-sample.jpg").outputStream().use {
        assertTrue("Sample must be encoded as JPEG", sample.compress(Bitmap.CompressFormat.JPEG, 92, it))
      }
    } finally {
      if (burned !== source) burned?.recycle()
      source.recycle()
    }
  }

  private fun createScene(): Bitmap {
    val bitmap = Bitmap.createBitmap(3000, 4000, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      shader = LinearGradient(
        0f, 0f, 0f, bitmap.height.toFloat(),
        Color.rgb(128, 201, 240), Color.rgb(104, 171, 112), Shader.TileMode.CLAMP,
      )
    }
    canvas.drawRect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), paint)
    paint.shader = null

    paint.color = Color.rgb(255, 232, 166)
    canvas.drawCircle(2420f, 640f, 240f, paint)

    paint.color = Color.rgb(245, 251, 255)
    canvas.drawOval(350f, 650f, 1100f, 870f, paint)
    canvas.drawOval(560f, 540f, 950f, 870f, paint)
    canvas.drawOval(1650f, 1070f, 2360f, 1280f, paint)

    paint.color = Color.rgb(125, 177, 126)
    canvas.drawOval(-850f, 2180f, 2550f, 6500f, paint)
    paint.color = Color.rgb(83, 147, 105)
    canvas.drawOval(650f, 2380f, 4050f, 6200f, paint)
    return bitmap
  }
}
