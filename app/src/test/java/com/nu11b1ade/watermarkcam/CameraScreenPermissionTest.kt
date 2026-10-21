package com.nu11b1ade.watermarkcam

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.nu11b1ade.watermarkcam.domain.Coordinates
import com.nu11b1ade.watermarkcam.domain.OverrideState
import com.nu11b1ade.watermarkcam.domain.PlaceOverride
import com.nu11b1ade.watermarkcam.domain.WatermarkConfig
import com.nu11b1ade.watermarkcam.domain.WatermarkContent
import com.nu11b1ade.watermarkcam.location.LocationPermissionState
import com.nu11b1ade.watermarkcam.ui.CameraScreen
import com.nu11b1ade.watermarkcam.ui.CameraUiState
import com.nu11b1ade.watermarkcam.ui.theme.WatermarkCamTheme
import java.time.ZonedDateTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "zh-rCN-w360dp-h640dp-port")
class CameraScreenPermissionTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  @Test fun `manual place still works but only manual place with coordinates hides the banner`() {
    val config = mutableStateOf(WatermarkConfig(overrides = OverrideState(place = PlaceOverride("陆家嘴"))))
    val observed = WatermarkContent(ZonedDateTime.parse("2026-10-21T09:08:07+08:00[Asia/Shanghai]"))
    compose.setContent {
      WatermarkCamTheme {
        CameraScreen(
          state = CameraUiState(config.value, observed), cameraGranted = true,
          locationPermission = LocationPermissionState.PermanentlyDenied,
          onRequestCamera = {}, onOpenPermissions = {}, onLocation = {}, onSettings = {},
          onCapture = { _, _ -> }, onViewPhoto = {}, previewPlaceholder = {},
        )
      }
    }
    compose.onNodeWithContentDescription("陆家嘴", substring = true).assertIsDisplayed()
    compose.onNodeWithText("需要位置权限才能显示地点和天气").assertIsDisplayed()

    compose.runOnIdle {
      config.value = config.value.copy(overrides = OverrideState(place = PlaceOverride("陆家嘴", Coordinates(31.2397, 121.4998))))
    }

    compose.onNodeWithText("需要位置权限才能显示地点和天气").assertDoesNotExist()
    compose.onNodeWithContentDescription("陆家嘴", substring = true).assertIsDisplayed()
  }
}
