package com.nu11b1ade.watermarkcam

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.nu11b1ade.watermarkcam.domain.WatermarkConfig
import com.nu11b1ade.watermarkcam.location.ForwardGeocoder
import com.nu11b1ade.watermarkcam.ui.SettingsScreen
import com.nu11b1ade.watermarkcam.ui.UpdateDialog
import com.nu11b1ade.watermarkcam.ui.theme.WatermarkCamTheme
import com.nu11b1ade.watermarkcam.update.ApkUrls
import com.nu11b1ade.watermarkcam.update.UpdateCheckState
import com.nu11b1ade.watermarkcam.update.UpdateDialogState
import com.nu11b1ade.watermarkcam.update.UpdateDownloadState
import com.nu11b1ade.watermarkcam.update.UpdateManifest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w411dp-h914dp-port")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UpdateUiTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  private val manifest = UpdateManifest(2, "0.2.0", "新增应用内更新\n修复问题，提升稳定性", ApkUrls(github = "https://github.com/app.apk"), "a".repeat(64))

  @Test fun `an old version gets advice but can still postpone or start updating`() {
    var postponed = 0
    var updates = 0
    compose.setContent {
      WatermarkCamTheme {
        UpdateDialog(UpdateDialogState(manifest, recommendImmediate = true), onUpdate = { updates++ },
          onDismiss = { postponed++ }, onCancelDownload = {})
      }
    }
    compose.onNodeWithText("发现新版本 v0.2.0").assertIsDisplayed()
    compose.onNodeWithText(manifest.notes).assertIsDisplayed()
    compose.onNodeWithText("当前版本过旧，建议立即更新").assertIsDisplayed()
    compose.onNodeWithText("更新").performClick()
    compose.onNodeWithText("以后再说").performClick()
    compose.runOnIdle { assertEquals(1, updates); assertEquals(1, postponed) }
  }

  @Test fun `downloading exposes progress and cancel, then failure allows retry`() {
    val state = mutableStateOf(UpdateDialogState(manifest, download = UpdateDownloadState.Downloading(0.42f)))
    var cancelled = 0
    var retries = 0
    compose.setContent {
      WatermarkCamTheme {
        UpdateDialog(state.value, onUpdate = { retries++ }, onDismiss = {}, onCancelDownload = { cancelled++ })
      }
    }
    compose.onNodeWithText("42%").assertIsDisplayed()
    compose.onNodeWithText("取消").performClick()
    compose.onNodeWithText("以后再说").assertDoesNotExist()
    compose.runOnIdle { state.value = state.value.copy(download = UpdateDownloadState.Failed) }
    compose.onNodeWithText("下载失败，请稍后再试").assertIsDisplayed()
    compose.onNodeWithText("更新").performClick()
    compose.runOnIdle { assertEquals(1, cancelled); assertEquals(1, retries) }
  }

  @Test fun `settings shows installed version and available version opens the update action`() {
    var opened = 0
    compose.setContent {
      WatermarkCamTheme {
        SettingsScreen(WatermarkConfig(), onSave = {}, onBack = {}, forwardGeocoder = ForwardGeocoder { emptyList() },
          currentVersionName = "0.1.0", updateState = UpdateCheckState.Available(manifest), onCheckUpdate = { opened++ })
      }
    }
    compose.onNodeWithText("发现新版本 v0.2.0").performScrollTo().performClick()
    compose.onNodeWithText("当前版本 v0.1.0").assertIsDisplayed()
    compose.runOnIdle { assertEquals(1, opened) }
  }
}
