package com.nu11b1ade.watermarkcam

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nu11b1ade.watermarkcam.domain.WatermarkConfig
import com.nu11b1ade.watermarkcam.domain.WatermarkSize
import com.nu11b1ade.watermarkcam.ui.SettingsScreen
import com.nu11b1ade.watermarkcam.ui.theme.WatermarkCamTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w360dp-h800dp-port")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsScreenTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  @Test fun `size starts at medium and saving returns the selected extra large size`() {
    var saved: WatermarkConfig? = null
    compose.setContent {
      WatermarkCamTheme {
        SettingsScreen(config = WatermarkConfig(), onSave = { saved = it }, onBack = {})
      }
    }

    compose.onNodeWithText("中").assertIsSelected()
    compose.onNodeWithText("特大").performClick().assertIsSelected()
    compose.onNodeWithText("保存设置").performClick()

    compose.runOnIdle { assertEquals(WatermarkConfig(watermarkSize = WatermarkSize.XLARGE), saved) }
  }
}
