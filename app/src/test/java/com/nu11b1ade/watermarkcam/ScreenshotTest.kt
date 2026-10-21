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
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.nu11b1ade.watermarkcam.domain.Coordinates
import com.nu11b1ade.watermarkcam.domain.OverrideState
import com.nu11b1ade.watermarkcam.domain.PlaceOverride
import com.nu11b1ade.watermarkcam.domain.PhotoFrame
import com.nu11b1ade.watermarkcam.domain.WatermarkConfig
import com.nu11b1ade.watermarkcam.domain.WatermarkContent
import com.nu11b1ade.watermarkcam.domain.WatermarkSize
import com.nu11b1ade.watermarkcam.domain.WeatherReading
import com.nu11b1ade.watermarkcam.domain.composeWatermark
import com.nu11b1ade.watermarkcam.history.OriginalRecord
import com.nu11b1ade.watermarkcam.history.PhotoEditState
import com.nu11b1ade.watermarkcam.location.ForwardGeocoder
import com.nu11b1ade.watermarkcam.location.PlaceCandidate
import com.nu11b1ade.watermarkcam.location.LocationPermissionState
import com.nu11b1ade.watermarkcam.save.WatermarkedJpegWriter
import com.nu11b1ade.watermarkcam.ui.CameraScreen
import com.nu11b1ade.watermarkcam.ui.AMapPrivacyDialog
import com.nu11b1ade.watermarkcam.ui.CameraUiState
import com.nu11b1ade.watermarkcam.ui.HistoryScreen
import com.nu11b1ade.watermarkcam.ui.OverrideDatePicker
import com.nu11b1ade.watermarkcam.ui.OverrideTimePicker
import com.nu11b1ade.watermarkcam.ui.PhotoEditScreen
import com.nu11b1ade.watermarkcam.ui.SettingsScreen
import com.nu11b1ade.watermarkcam.ui.UpdateDialog
import com.nu11b1ade.watermarkcam.ui.theme.WatermarkCamTheme
import com.nu11b1ade.watermarkcam.update.ApkUrls
import com.nu11b1ade.watermarkcam.update.UpdateCheckState
import com.nu11b1ade.watermarkcam.update.UpdateDialogState
import com.nu11b1ade.watermarkcam.update.UpdateManifest
import java.io.File
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.TemporaryFolder
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

/**
 * JVM "screenshots" for humans to eyeball (no emulator needed). Writes to <repo>/.scratch/screenshots/.
 * Pixel 7-ish: 1080x2400 @ 420dpi, zh-CN.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "zh-rCN-w411dp-h914dp-port-420dpi")
class ScreenshotTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  @get:Rule val temporary = TemporaryFolder()

  private val coords = Coordinates(31.2304, 121.4737)
  private val observed = WatermarkContent(
    capturedAt = ZonedDateTime.parse("2026-10-21T09:08:07+08:00[Asia/Shanghai]"),
    place = "上海市黄浦区南京东路",
    coordinates = coords,
    weather = WeatherReading(coords, 22.0, "多云"),
  )
  private val config = WatermarkConfig()
  private val update = UpdateManifest(
    2, "0.2.0", "本次更新\n\n• 新增应用内更新，支持自动检查与手动检查。\n• 下载失败时自动切换备用来源。\n• 优化拍照体验，修复问题，提升稳定性。",
    ApkUrls(github = "https://github.com/nu11b1ade/watermark-cam/releases/download/v0.2.0/watermark-cam-0.2.0.apk"), "a".repeat(64),
  )

  @Test fun mainCameraScreen() = shoot("v3-main.png") {
    CameraScreen(
      state = CameraUiState(config = config, observed = observed),
      cameraGranted = true, locationPermission = LocationPermissionState.Granted,
      onRequestCamera = {}, onOpenPermissions = {}, onLocation = {}, onSettings = {},
      onCapture = { _, _ -> }, onViewPhoto = {},
      previewPlaceholder = { FakeStreet() },
    )
  }

  @Test fun cameraLocationDenied() {
    var authorizations = 0
    shoot("v6-location-denied.png", beforeCapture = {
      compose.onNodeWithText("需要位置权限才能显示地点和天气").assertIsDisplayed()
      compose.onNodeWithText("授权").performClick()
      compose.onNodeWithText("去设置").assertDoesNotExist()
      compose.onNodeWithContentDescription("拍照").assertIsDisplayed().assertIsEnabled()
      assertEquals(1, authorizations)
    }) {
      CameraScreen(
        state = CameraUiState(config, observed.copy(place = "未开启定位（可手动设置）", coordinates = null, weather = null)),
        cameraGranted = true, locationPermission = LocationPermissionState.CanAsk,
        onRequestCamera = {}, onOpenPermissions = {}, onLocation = { authorizations++ }, onSettings = {},
        onCapture = { _, _ -> }, onViewPhoto = {}, previewPlaceholder = { FakeStreet() },
      )
    }
  }

  @Test fun cameraLocationPermanentlyDenied() {
    var settingsOpened = 0
    shoot("v6-location-permanently-denied.png", beforeCapture = {
      val banner = compose.onNodeWithText("需要位置权限才能显示地点和天气").assertIsDisplayed()
      compose.onNodeWithText("授权").assertIsDisplayed()
      compose.onNodeWithText("去设置").performClick()
      val shutter = compose.onNodeWithContentDescription("拍照").assertIsDisplayed().assertIsEnabled()
      assertTrue(banner.fetchSemanticsNode().boundsInRoot.bottom < shutter.fetchSemanticsNode().boundsInRoot.top)
      assertEquals(1, settingsOpened)
    }) {
      CameraScreen(
        state = CameraUiState(config, observed.copy(place = "未开启定位（可手动设置）", coordinates = null, weather = null)),
        cameraGranted = true, locationPermission = LocationPermissionState.PermanentlyDenied,
        onRequestCamera = {}, onOpenPermissions = { settingsOpened++ }, onLocation = {}, onSettings = {},
        onCapture = { _, _ -> }, onViewPhoto = {}, previewPlaceholder = { FakeStreet() },
      )
    }
  }

  @Test fun settingsScreen() = shoot("v3-settings.png") {
    SettingsScreen(
      config = config.copy(overrides = OverrideState(place = PlaceOverride("上海市黄浦区南京东路", coords))),
      onSave = {}, onBack = {}, forwardGeocoder = ForwardGeocoder { emptyList() },
    )
  }

  @Test fun timeWheelDialog() = shoot("v6-time-wheel-dialog.png", captureDialog = true, beforeCapture = {
    compose.onNodeWithText("选择时间").assertIsDisplayed()
    compose.onNodeWithContentDescription("时").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "09时"))
    compose.onNodeWithContentDescription("分").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "08分"))
    compose.onNodeWithText("取消").assertIsDisplayed()
    compose.onNodeWithText("确定").assertIsDisplayed().assertIsEnabled()
  }) {
    OverrideTimePicker(observed.capturedAt.toLocalDateTime(), onDismiss = {}, onConfirm = { _, _ -> })
  }

  @Config(qualifiers = "zh-rCN-w411dp-h914dp-port-night-420dpi")
  @Test fun dateWheelDialog() = shoot("v6-date-wheel-dialog.png", captureDialog = true, beforeCapture = {
    compose.onNodeWithText("选择日期").assertIsDisplayed()
    compose.onNodeWithContentDescription("年").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "2026年"))
    compose.onNodeWithContentDescription("月").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "10月"))
    compose.onNodeWithContentDescription("日").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "21日"))
    compose.onNodeWithText("取消").assertIsDisplayed()
    compose.onNodeWithText("确定").assertIsDisplayed().assertIsEnabled()
  }) {
    OverrideDatePicker(observed.capturedAt.toLocalDate(), onDismiss = {}, yearRange = 2021..2027, onConfirm = {})
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

  @Test fun amapPlaceCandidates() = shoot("v7-amap-candidates.png", beforeCapture = {
    compose.onNodeWithText("地点 / 地址").performScrollTo().performTextReplacement("朝阳公园")
    compose.mainClock.advanceTimeBy(900)
    compose.onNodeWithText("北京市朝阳区农展南路1号").performScrollTo().assertIsDisplayed()
    compose.onNodeWithText("候选地点 · 点击选择").assertIsDisplayed()
    compose.onNodeWithText("朝阳公园南门").assertIsDisplayed()
    compose.onNodeWithText("朝阳公园西门").assertIsDisplayed()
  }) {
    SettingsScreen(
      config.copy(overrides = OverrideState(place = PlaceOverride("北京"))), onSave = {}, onBack = {}, amapConsent = true,
      forwardGeocoder = ForwardGeocoder {
        listOf(
          PlaceCandidate("北京朝阳公园", "北京市朝阳区朝阳公园南路1号", Coordinates(39.9337, 116.4731)),
          PlaceCandidate("朝阳公园南门", "北京市朝阳区朝阳公园南路", null, "south-gate"),
          PlaceCandidate("朝阳公园西门", "北京市朝阳区农展南路1号", Coordinates(39.9376, 116.4702)),
        )
      },
    )
  }

  @Test fun amapPrivacyDialog() = shoot("v7-amap-privacy-dialog.png", captureDialog = true, beforeCapture = {
    compose.onNodeWithText("高德地点服务隐私说明").assertIsDisplayed()
    compose.onNodeWithText("阅读高德隐私政策").assertIsDisplayed()
    compose.onNodeWithText("暂不使用高德").assertIsDisplayed().assertIsEnabled()
    compose.onNodeWithText("同意并启用").assertIsDisplayed().assertIsEnabled()
  }) {
    CameraScreen(
      state = CameraUiState(config = config, observed = observed), cameraGranted = false,
      locationPermission = LocationPermissionState.CanAsk, onRequestCamera = {}, onOpenPermissions = {},
      onLocation = {}, onSettings = {}, onCapture = { _, _ -> }, onViewPhoto = {},
    )
    AMapPrivacyDialog(onChoice = {}, onOpenPolicy = {})
  }

  @Test fun settingsUpdateSection() = shoot("v5-update.png", beforeCapture = {
    compose.onNodeWithText("发现新版本 v0.2.0").performScrollTo().assertIsDisplayed()
    compose.onNodeWithText("当前版本 v${BuildConfig.VERSION_NAME}").assertIsDisplayed()
  }) {
    SettingsScreen(config, onSave = {}, onBack = {}, forwardGeocoder = ForwardGeocoder { emptyList() },
      updateState = UpdateCheckState.Available(update))
  }

  @Test fun updateDialog() = shoot("v5-update-dialog.png", captureDialog = true, beforeCapture = {
    compose.onNodeWithText("发现新版本 v0.2.0").assertIsDisplayed()
    compose.onNodeWithText("以后再说").assertIsDisplayed()
  }) {
    UpdateDialog(UpdateDialogState(update, recommendImmediate = true), onUpdate = {}, onDismiss = {}, onCancelDownload = {})
  }

  @Test fun savedPhotoSample() = savedPhotoSample("v3-watermark-sample.jpg", config)

  @Test fun savedPhotoSampleLarge() = savedPhotoSample("v3-watermark-sample-large.jpg", config.copy(watermarkSize = WatermarkSize.XLARGE))

  @Test fun historyList() {
    val original = sampleOriginal()
    val records = listOf("上海市黄浦区南京东路", "上海市浦东新区陆家嘴", "人民公园南门").mapIndexed { index, place ->
      OriginalRecord("history-$index", 1792544887000L - index * 86_400_000L, "content://photos/$index",
        observed.copy(capturedAt = observed.capturedAt.minusDays(index.toLong()), place = place), config, PhotoFrame(0.82))
    }
    shoot("v4-history.png", beforeCapture = {
      compose.waitUntil(10_000) {
        compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("历史照片缩略图", substring = true)).fetchSemanticsNodes().size == 3
      }
    }) {
      HistoryScreen(records, originalFile = { original }, retentionDays = 7, onEdit = {}, onBack = {})
    }
  }

  @Config(qualifiers = "zh-rCN-w411dp-h2300dp-port-420dpi")
  @Test fun editPastPhoto() {
    val original = sampleOriginal()
    val record = OriginalRecord("edit", 1792544887000L, "content://photos/1", observed, config, PhotoFrame(0.82))
    val edited = PhotoEditState.from(record).copy(time = "2025-05-06 07:08:09", place = "上海市浦东新区陆家嘴",
      latitude = "31.2397", longitude = "121.4998", weatherText = "多云 23°C",
      config = config.copy(showCustomText = true, customText = "今日打卡 · 平安喜乐", watermarkSize = WatermarkSize.LARGE))
    shoot("v4-edit.png", beforeCapture = {
      // Flush snapshot notifications and advance scheduled main-looper frames: after other
      // screenshots, an IO result may be delivered while the UI still shows the loading state.
      val deadline = System.currentTimeMillis() + 15_000
      while (compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("照片预览")).fetchSemanticsNodes().isEmpty()) {
        compose.onNodeWithText("无法读取原图，请返回重试").assertDoesNotExist()
        check(System.currentTimeMillis() < deadline) { "照片预览 never appeared" }
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50))
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeByFrame()
        Thread.sleep(50)
      }
    }) {
      PhotoEditScreen(record, original, onSave = {}, onBack = {}, forwardGeocoder = ForwardGeocoder { emptyList() }, initial = edited)
    }
  }

  private fun sampleOriginal(): File {
    val file = temporary.newFile("original.jpg")
    val bitmap = Bitmap.createBitmap(900, 1200, Bitmap.Config.ARGB_8888)
    try {
      Canvas(bitmap).drawStreet(900f, 1200f)
      file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 94, it) }
    } finally { bitmap.recycle() }
    return file
  }

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

  private fun shoot(name: String, beforeCapture: () -> Unit = {}, captureDialog: Boolean = false, content: @Composable () -> Unit) {
    compose.setContent { WatermarkCamTheme { content() } }
    compose.waitForIdle()
    compose.mainClock.advanceTimeBy(1_000)
    compose.waitForIdle()
    beforeCapture()
    compose.waitForIdle()
    // Compose dialogs have their own window; the Activity decor alone would produce a blank shot.
    val view = if (captureDialog) checkNotNull(ShadowDialog.getLatestDialog()).window!!.decorView
      else compose.activity.window.decorView
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
