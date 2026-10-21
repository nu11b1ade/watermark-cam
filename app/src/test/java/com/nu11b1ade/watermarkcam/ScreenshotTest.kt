package com.nu11b1ade.watermarkcam

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.nu11b1ade.watermarkcam.domain.Coordinates
import com.nu11b1ade.watermarkcam.domain.OverrideState
import com.nu11b1ade.watermarkcam.domain.PlaceOverride
import com.nu11b1ade.watermarkcam.domain.WatermarkConfig
import com.nu11b1ade.watermarkcam.domain.WatermarkContent
import com.nu11b1ade.watermarkcam.domain.WatermarkSize
import com.nu11b1ade.watermarkcam.domain.WeatherReading
import com.nu11b1ade.watermarkcam.domain.composeWatermark
import com.nu11b1ade.watermarkcam.location.ForwardGeocoder
import com.nu11b1ade.watermarkcam.location.PlaceCandidate
import com.nu11b1ade.watermarkcam.save.WatermarkedJpegWriter
import com.nu11b1ade.watermarkcam.ui.CameraScreen
import com.nu11b1ade.watermarkcam.ui.CameraUiState
import com.nu11b1ade.watermarkcam.ui.SettingsScreen
import com.nu11b1ade.watermarkcam.ui.theme.WatermarkCamTheme
import java.io.File
import java.time.ZonedDateTime
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * JVM "screenshots" for humans to eyeball (no emulator needed). Writes to <repo>/.scratch/screenshots/.
 * Pixel 7-ish: 1080x2400 @ 420dpi, zh-CN.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "zh-rCN-w411dp-h914dp-port-420dpi")
class ScreenshotTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private val coords = Coordinates(31.2304, 121.4737)
  private val observed = WatermarkContent(
    capturedAt = ZonedDateTime.parse("2026-10-21T09:08:07+08:00[Asia/Shanghai]"),
    place = "上海市黄浦区南京东路",
    coordinates = coords,
    weather = WeatherReading(coords, 22.0, "多云"),
  )
  private val config = WatermarkConfig()

  @Test fun mainCameraScreen() = shoot("v3-main.png") {
    CameraScreen(
      state = CameraUiState(config = config, observed = observed),
      cameraGranted = true, locationGranted = true,
      onRequestCamera = {}, onOpenPermissions = {}, onLocation = {}, onSettings = {},
      onCapture = { _, _ -> }, onViewPhoto = {},
      previewPlaceholder = { FakeStreet() },
    )
  }

  @Test fun settingsScreen() = shoot("v3-settings.png") {
    SettingsScreen(
      config = config.copy(overrides = OverrideState(place = PlaceOverride("上海市黄浦区南京东路", coords))),
      onSave = {}, onBack = {}, forwardGeocoder = ForwardGeocoder { emptyList() },
    )
  }

  /** Same screen on a very tall window so every section (incl. 手动地点) is visible in one image. */
  @Config(qualifiers = "zh-rCN-w411dp-h1800dp-port-420dpi")
  @Test fun settingsScreenFull() = shoot("v3-settings-full.png") {
    SettingsScreen(
      config = config.copy(overrides = OverrideState(place = PlaceOverride("上海市黄浦区南京东路", coords))),
      onSave = {}, onBack = {}, forwardGeocoder = ForwardGeocoder { emptyList() },
    )
  }

  @Config(qualifiers = "zh-rCN-w411dp-h1100dp-port-420dpi")
  @Test fun settingsPlaceCandidates() = shoot("v4-settings-place.png", beforeCapture = {
    compose.onNodeWithText("查找经纬度").performScrollTo().performClick()
    compose.onNodeWithText("经度（可自动填写）").performScrollTo()
    compose.onNodeWithText("上海市 浦东新区 陆家嘴").assertIsDisplayed()
    compose.onNodeWithText("上海市 浦东新区 上海中心").assertIsDisplayed()
  }) {
    SettingsScreen(
      config = config.copy(overrides = OverrideState(place = PlaceOverride("陆家嘴"))),
      onSave = {}, onBack = {},
      forwardGeocoder = ForwardGeocoder {
        listOf(
          PlaceCandidate("上海市 浦东新区 陆家嘴", "陆家嘴, 浦东新区, 上海市, 中国", Coordinates(31.2397, 121.4998)),
          PlaceCandidate("上海市 浦东新区 东方明珠", "东方明珠, 浦东新区, 上海市, 中国", Coordinates(31.2396, 121.4997)),
          PlaceCandidate("上海市 浦东新区 上海中心", "上海中心, 浦东新区, 上海市, 中国", Coordinates(31.2336, 121.5055)),
        )
      },
    )
  }

  @Test fun savedPhotoSample() = savedPhotoSample("v3-watermark-sample.jpg", config)

  @Test fun savedPhotoSampleLarge() = savedPhotoSample("v3-watermark-sample-large.jpg", config.copy(watermarkSize = WatermarkSize.XLARGE))

  private fun savedPhotoSample(name: String, sampleConfig: WatermarkConfig) {
    val width = 3000
    val height = 4000
    val scene = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    Canvas(scene).drawStreet(width.toFloat(), height.toFloat())
    val source = File.createTempFile("sample", ".jpg")
    source.outputStream().use { scene.compress(Bitmap.CompressFormat.JPEG, 92, it) }
    scene.recycle()
    val out = outFile(name)
    try {
      WatermarkedJpegWriter().write(source, out, composeWatermark(sampleConfig, observed))
    } finally {
      source.delete()
    }
    assertTrue(out.length() > 0)
  }

  private fun shoot(name: String, beforeCapture: () -> Unit = {}, content: @Composable () -> Unit) {
    compose.setContent { WatermarkCamTheme { content() } }
    compose.waitForIdle()
    compose.mainClock.advanceTimeBy(1_000)
    compose.waitForIdle()
    beforeCapture()
    compose.waitForIdle()
    val view = compose.activity.window.decorView
    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    view.draw(Canvas(bitmap))
    outFile(name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    bitmap.recycle()
  }

  private fun outFile(name: String): File {
    val dir = File(System.getProperty("user.dir")!!).let { if (it.name == "app") it.parentFile!! else it }
      .resolve(".scratch/screenshots").apply { mkdirs() }
    return dir.resolve(name)
  }
}

/** Stand-in for the camera feed: sky, buildings, street. */
@Composable
private fun FakeStreet() {
  ComposeCanvas(Modifier.fillMaxSize()) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF6FA8DC), Color(0xFFBFD7EA))), size = Size(size.width, size.height * 0.55f))
    val blocks = listOf(0.00f to 0.30f, 0.18f to 0.18f, 0.34f to 0.38f, 0.58f to 0.24f, 0.78f to 0.34f)
    blocks.forEachIndexed { i, (x, h) ->
      drawRect(Color(if (i % 2 == 0) 0xFF8E8A84 else 0xFFA49C90),
        topLeft = Offset(size.width * x, size.height * (0.55f - h)), size = Size(size.width * 0.24f, size.height * h))
    }
    drawRect(Brush.verticalGradient(listOf(Color(0xFF6B6B6B), Color(0xFF3E3E3E)), startY = size.height * 0.55f),
      topLeft = Offset(0f, size.height * 0.55f), size = Size(size.width, size.height * 0.45f))
  }
}

private fun Canvas.drawStreet(w: Float, h: Float) {
  val p = Paint(Paint.ANTI_ALIAS_FLAG)
  p.shader = LinearGradient(0f, 0f, 0f, h * 0.55f, 0xFF6FA8DC.toInt(), 0xFFBFD7EA.toInt(), Shader.TileMode.CLAMP)
  drawRect(0f, 0f, w, h * 0.55f, p)
  p.shader = null
  listOf(0.00f to 0.30f, 0.18f to 0.18f, 0.34f to 0.38f, 0.58f to 0.24f, 0.78f to 0.34f).forEachIndexed { i, (x, bh) ->
    p.color = if (i % 2 == 0) 0xFF8E8A84.toInt() else 0xFFA49C90.toInt()
    drawRect(w * x, h * (0.55f - bh), w * (x + 0.24f), h * 0.55f, p)
  }
  p.shader = LinearGradient(0f, h * 0.55f, 0f, h, 0xFF6B6B6B.toInt(), 0xFF3E3E3E.toInt(), Shader.TileMode.CLAMP)
  drawRect(0f, h * 0.55f, w, h, p)
}
