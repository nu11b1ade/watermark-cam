package com.nu11b1ade.watermarkcam

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import com.nu11b1ade.watermarkcam.domain.Coordinates
import com.nu11b1ade.watermarkcam.domain.OverrideState
import com.nu11b1ade.watermarkcam.domain.PlaceOverride
import com.nu11b1ade.watermarkcam.domain.WatermarkConfig
import com.nu11b1ade.watermarkcam.domain.WatermarkSize
import com.nu11b1ade.watermarkcam.location.ForwardGeocoder
import com.nu11b1ade.watermarkcam.location.LocationReading
import com.nu11b1ade.watermarkcam.location.PlaceCandidate
import com.nu11b1ade.watermarkcam.ui.SettingsScreen
import com.nu11b1ade.watermarkcam.ui.theme.WatermarkCamTheme
import java.io.IOException
import java.time.LocalDateTime
import kotlinx.coroutines.CompletableDeferred
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
        SettingsScreen(config = WatermarkConfig(), onSave = { saved = it }, onBack = {}, forwardGeocoder = ForwardGeocoder { emptyList() })
      }
    }

    compose.onNodeWithText("中").assertIsSelected()
    compose.onNodeWithText("特大").performClick().assertIsSelected()
    compose.onNodeWithText("保存设置").performClick()

    compose.runOnIdle { assertEquals(WatermarkConfig(watermarkSize = WatermarkSize.XLARGE), saved) }
  }

  @Test fun `custom time wheel changes are discarded on cancel and saved on confirm`() {
    val original = LocalDateTime.of(2026, 10, 21, 9, 8, 7)
    var saved: WatermarkConfig? = null
    showSettings(WatermarkConfig(overrides = OverrideState(time = original)), onSave = { saved = it })
    compose.onNodeWithText("时间 · 24小时制").performScrollTo().performClick()
    compose.onNodeWithContentDescription("时").performSemanticsAction(SemanticsActions.SetProgress) { it(10f) }
    compose.onNodeWithText("取消").performClick()
    compose.onNodeWithText("09:08:07").assertIsDisplayed()

    compose.onNodeWithText("时间 · 24小时制").performClick()
    compose.onNodeWithContentDescription("时").performSemanticsAction(SemanticsActions.SetProgress) { it(10f) }
    compose.onNodeWithContentDescription("分").performSemanticsAction(SemanticsActions.SetProgress) { it(11f) }
    compose.onNodeWithText("确定").performClick()
    compose.onNodeWithText("10:11:00").assertIsDisplayed()
    compose.onNodeWithText("保存设置").performClick()

    compose.runOnIdle { assertEquals(original.withHour(10).withMinute(11).withSecond(0), saved?.overrides?.time) }
  }

  private val candidates = listOf(
    PlaceCandidate("上海市 浦东新区 陆家嘴", "陆家嘴, 浦东新区, 上海市, 中国", Coordinates(31.239712, 121.499812)),
    PlaceCandidate("上海市 浦东新区 东方明珠", "东方明珠, 浦东新区, 上海市, 中国", Coordinates(31.2396, 121.4997)),
  )

  @Test fun `opening a saved place does not search and typing waits for debounce before filling coordinates`() {
    val queries = mutableListOf<String>()
    val config = WatermarkConfig(overrides = OverrideState(place = PlaceOverride("已保存地点", Coordinates(39.9, 116.4))))
    showSettings(config, ForwardGeocoder { queries.add(it); candidates })
    compose.mainClock.advanceTimeBy(1_000)
    compose.runOnIdle { assertEquals(emptyList<String>(), queries) }
    compose.onNodeWithText("纬度（可自动填写）").assertTextContains("39.9")

    compose.onNodeWithText("地点 / 地址").performScrollTo().performTextReplacement("陆家嘴")
    compose.mainClock.advanceTimeBy(600)
    compose.onNodeWithText("地点 / 地址").performTextReplacement("陆家嘴 上海")
    compose.mainClock.advanceTimeBy(700)
    compose.runOnIdle { assertEquals(emptyList<String>(), queries) }
    compose.mainClock.advanceTimeBy(200)

    compose.runOnIdle { assertEquals(listOf("陆家嘴 上海"), queries) }
    compose.onNodeWithText("地点 / 地址").assertTextContains("陆家嘴 上海")
    compose.onNodeWithText("纬度（可自动填写）").assertTextContains("31.2397")
    compose.onNodeWithText("经度（可自动填写）").assertTextContains("121.4998")
  }

  @Test fun `manual typing and sign toggle survive debounce but button and candidate reset protection`() {
    val queries = mutableListOf<String>()
    showSettings(geocoder = ForwardGeocoder { queries.add(it); candidates })
    compose.onNodeWithText("地点 / 地址").performScrollTo().performTextReplacement("手写地点")
    compose.onNodeWithText("纬度（可自动填写）").performScrollTo().performTextReplacement("12.3456")
    compose.onNodeWithText("经度（可自动填写）").performScrollTo().performTextReplacement("121.0000")
    compose.onNodeWithContentDescription("切换经度正负号").performClick()
    compose.mainClock.advanceTimeBy(900)

    compose.onNodeWithText("纬度（可自动填写）").assertTextContains("12.3456")
    compose.onNodeWithText("经度（可自动填写）").assertTextContains("-121.0000")
    compose.onNodeWithText(candidates.first().name).assertExists()

    compose.onNodeWithText("查找经纬度").performScrollTo().performClick()
    compose.mainClock.advanceTimeByFrame()
    compose.onNodeWithText("纬度（可自动填写）").assertTextContains("31.2397")
    compose.onNodeWithText("经度（可自动填写）").assertTextContains("121.4998")

    compose.onNodeWithText("纬度（可自动填写）").performScrollTo().performTextReplacement("10")
    compose.onNodeWithText(candidates[1].name).performScrollTo().performClick()
    compose.mainClock.advanceTimeBy(1_000)
    compose.onNodeWithText("地点 / 地址").assertTextContains(candidates[1].name)
    compose.onNodeWithText("纬度（可自动填写）").assertTextContains("31.2396")
    compose.onNodeWithText("纬度 31.2397, 经度 121.4998").assertDoesNotExist()
    compose.runOnIdle { assertEquals(listOf("手写地点", "手写地点"), queries) }

    compose.onNodeWithText("地点 / 地址").performScrollTo().performTextReplacement("新的地点")
    compose.mainClock.advanceTimeBy(900)
    compose.onNodeWithText("纬度（可自动填写）").assertTextContains("31.2397")
    compose.runOnIdle { assertEquals(listOf("手写地点", "手写地点", "新的地点"), queries) }
  }

  @Test fun `current location fills the real reverse geocoded address and coordinates without a forward search`() {
    var saved: WatermarkConfig? = null
    showSettings(
      geocoder = ForwardGeocoder { throw AssertionError("Current location must not forward geocode") },
      currentLocation = { LocationReading("上海市浦东新区世纪大道1号", Coordinates(31.239712, 121.499812)) },
      onSave = { saved = it },
    )

    compose.onNodeWithText("用当前位置").performScrollTo().performClick()
    compose.onNodeWithText("保存设置").performClick()
    compose.runOnIdle {
      assertEquals(PlaceOverride("上海市浦东新区世纪大道1号", Coordinates(31.2397, 121.4998)), saved?.overrides?.place)
    }
  }

  @Test fun `old lookup completing before recomposition cannot erase the next typed lookup`() {
    val firstResponse = CompletableDeferred<List<PlaceCandidate>>()
    val queries = mutableListOf<String>()
    showSettings(geocoder = ForwardGeocoder { query ->
      queries.add(query)
      if (query == "陆家嘴") firstResponse.await() else listOf(candidates[1])
    })
    compose.onNodeWithText("查找经纬度").performScrollTo().performClick()
    compose.onNodeWithText("正在查找…").assertExists()
    val editPlace = compose.onNodeWithText("地点 / 地址").fetchSemanticsNode().config[SemanticsActions.SetText].action!!

    // Both events happen in one UI turn, before LaunchedEffect's old key can be cancelled.
    compose.runOnIdle {
      editPlace(AnnotatedString("新的地点"))
      firstResponse.complete(listOf(candidates.first()))
    }
    compose.mainClock.advanceTimeBy(900)

    compose.runOnIdle { assertEquals(listOf("陆家嘴", "新的地点"), queries) }
    compose.onNodeWithText("地点 / 地址").assertTextContains("新的地点")
    compose.onNodeWithText("纬度（可自动填写）").assertTextContains("31.2396")
    compose.onNodeWithText("经度（可自动填写）").assertTextContains("121.4997")
  }

  @Test fun `coordinate edits made while a button lookup is pending protect against its result and later debounce`() {
    val response = CompletableDeferred<List<PlaceCandidate>>()
    showSettings(geocoder = ForwardGeocoder { response.await() })
    compose.onNodeWithText("查找经纬度").performScrollTo().performClick()
    compose.onNodeWithText("正在查找…").assertExists()
    compose.onNodeWithText("纬度（可自动填写）").performScrollTo().performTextReplacement("13.5000")
    compose.onNodeWithText("经度（可自动填写）").performScrollTo().performTextReplacement("120.5000")
    compose.onNodeWithContentDescription("切换经度正负号").performClick()
    compose.runOnIdle { response.complete(candidates) }

    compose.onNodeWithText("纬度（可自动填写）").assertTextContains("13.5000")
    compose.onNodeWithText("经度（可自动填写）").assertTextContains("-120.5000")
    compose.onNodeWithText(candidates.first().name).assertExists()
    compose.onNodeWithText("地点 / 地址").performScrollTo().performTextReplacement("新的地点")
    compose.mainClock.advanceTimeBy(900)
    compose.onNodeWithText("纬度（可自动填写）").assertTextContains("13.5000")
    compose.onNodeWithText("经度（可自动填写）").assertTextContains("-120.5000")
  }

  @Test fun `lookup failure and missing current location permission have actionable messages`() {
    showSettings(geocoder = ForwardGeocoder { throw IOException("offline") }, currentLocation = { throw SecurityException("No permission") })
    compose.onNodeWithText("查找经纬度").performScrollTo().performClick()
    compose.onNodeWithText("查找失败，请检查网络，或手动填经纬度").performScrollTo().assertIsDisplayed()

    compose.onNodeWithText("用当前位置").performScrollTo().performClick()
    compose.onNodeWithText("需要定位权限才能使用当前位置").performScrollTo().assertIsDisplayed()
  }

  @Test fun `empty lookup and unavailable current location preserve the editable draft`() {
    showSettings()
    compose.onNodeWithText("查找经纬度").performScrollTo().performClick()
    compose.onNodeWithText("没找到这个地点，可以手动填经纬度").performScrollTo().assertIsDisplayed()
    compose.onNodeWithText("用当前位置").performScrollTo().performClick()
    compose.onNodeWithText("定位失败，请检查定位设置，或手动填写地点和经纬度").performScrollTo().assertIsDisplayed()
    compose.onNodeWithText("地点 / 地址").assertTextContains("陆家嘴")
  }

  private fun showSettings(
    config: WatermarkConfig = WatermarkConfig(overrides = OverrideState(place = PlaceOverride("陆家嘴"))),
    geocoder: ForwardGeocoder = ForwardGeocoder { emptyList() },
    currentLocation: suspend () -> LocationReading? = { null },
    onSave: (WatermarkConfig) -> Unit = {},
  ) {
    compose.setContent {
      WatermarkCamTheme {
        SettingsScreen(config = config, onSave = onSave, onBack = {}, forwardGeocoder = geocoder, onUseCurrentLocation = currentLocation)
      }
    }
    compose.waitForIdle()
  }
}
