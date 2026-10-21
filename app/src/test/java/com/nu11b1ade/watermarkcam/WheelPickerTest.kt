package com.nu11b1ade.watermarkcam

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.nu11b1ade.watermarkcam.ui.OverrideDatePicker
import com.nu11b1ade.watermarkcam.ui.OverrideTimePicker
import com.nu11b1ade.watermarkcam.ui.WheelPicker
import com.nu11b1ade.watermarkcam.ui.theme.WatermarkCamTheme
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "zh-rCN-w411dp-h914dp-port")
class WheelPickerTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  @Test fun `the hour wheel loops from 23 to 00 and reports the centered selection`() {
    val selected = mutableStateOf(23)
    val changes = mutableListOf<Int>()
    compose.setContent {
      WatermarkCamTheme {
        WheelPicker(0..23, selected.value, { selected.value = it; changes += it }, label = "时")
      }
    }
    compose.onNodeWithContentDescription("时").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "23时"))

    compose.onNodeWithText("00").performClick()

    val wheel = compose.onNodeWithContentDescription("时")
      .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "00时")).fetchSemanticsNode()
    val centered = compose.onNodeWithText("00").assertIsSelected().fetchSemanticsNode()
    assertEquals(wheel.boundsInRoot.center.y, centered.boundsInRoot.center.y, 1f)
    assertEquals(listOf(0), changes)
  }

  @Test fun `dragging the minute wheel through 59 snaps to centered 00`() {
    val selected = mutableStateOf(59)
    compose.setContent {
      WatermarkCamTheme { WheelPicker(0..59, selected.value, { selected.value = it }, label = "分") }
    }

    compose.onNodeWithContentDescription("分").performTouchInput {
      swipe(center, center - Offset(0f, height / 5f * 1.1f), durationMillis = 1_000)
    }

    val wheel = compose.onNodeWithContentDescription("分").fetchSemanticsNode()
    val centered = compose.onNodeWithText("00").assertIsSelected().fetchSemanticsNode()
    assertEquals(0, selected.value)
    assertEquals(wheel.boundsInRoot.center.y, centered.boundsInRoot.center.y, 1f)
  }

  @Test fun `selection is reported after animation settles rather than when a row is tapped`() {
    val selected = mutableStateOf(23)
    val changes = mutableListOf<Int>()
    compose.setContent {
      WatermarkCamTheme { WheelPicker(0..23, selected.value, { selected.value = it; changes += it }, label = "时") }
    }
    compose.onNodeWithText("23").assertIsSelected()
    compose.mainClock.autoAdvance = false

    compose.onNodeWithText("00").performClick()
    compose.runOnIdle { assertEquals(emptyList<Int>(), changes) }
    compose.mainClock.advanceTimeBy(1_000)
    compose.mainClock.autoAdvance = true
    compose.waitForIdle()

    assertEquals(listOf(0), changes)
  }

  @Test fun `the finite year wheel centers both ends and follows external value changes`() {
    val selected = mutableStateOf(2027)
    compose.setContent {
      WatermarkCamTheme {
        WheelPicker(2021..2027, selected.value, { selected.value = it }, "年", looping = false, formatValue = Int::toString)
      }
    }
    compose.onNodeWithText("2028").assertDoesNotExist()
    compose.onNodeWithContentDescription("年").performSemanticsAction(SemanticsActions.SetProgress) { it(2030f) }
    compose.onNodeWithText("2027").assertIsSelected()

    compose.runOnIdle { selected.value = 2021 }

    val wheel = compose.onNodeWithContentDescription("年").fetchSemanticsNode()
    val centered = compose.onNodeWithText("2021").assertIsSelected().fetchSemanticsNode()
    compose.onNodeWithText("2020").assertDoesNotExist()
    assertEquals(wheel.boundsInRoot.center.y, centered.boundsInRoot.center.y, 1f)
  }

  @Test fun `changing the year clamps February 29 before confirming the date`() {
    var confirmed: LocalDate? = null
    compose.setContent {
      WatermarkCamTheme {
        OverrideDatePicker(LocalDate.of(2024, 2, 29), onDismiss = {}, yearRange = 2021..2027, onConfirm = { confirmed = it })
      }
    }

    compose.onNodeWithContentDescription("年").performSemanticsAction(SemanticsActions.SetProgress) { it(2025f) }
    compose.onNodeWithContentDescription("日").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "28日"))
    compose.onNodeWithText("确定").performClick()

    assertEquals(LocalDate.of(2025, 2, 28), confirmed)
  }

  @Test fun `changing the month clamps day 31 and removes it from the day wheel`() {
    var confirmed: LocalDate? = null
    compose.setContent {
      WatermarkCamTheme {
        OverrideDatePicker(LocalDate.of(2026, 3, 31), onDismiss = {}, yearRange = 2021..2027, onConfirm = { confirmed = it })
      }
    }

    compose.onNodeWithContentDescription("月").performSemanticsAction(SemanticsActions.SetProgress) { it(4f) }
    compose.onNodeWithContentDescription("日").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "30日"))
    compose.onNodeWithText("31").assertDoesNotExist()
    compose.onNodeWithText("确定").performClick()

    assertEquals(LocalDate.of(2026, 4, 30), confirmed)
  }

  @Test fun `time confirmation returns the selected 24 hour wheel values`() {
    var confirmed: Pair<Int, Int>? = null
    compose.setContent {
      WatermarkCamTheme {
        OverrideTimePicker(LocalDateTime.of(2026, 10, 21, 23, 59), onDismiss = {}, onConfirm = { hour, minute -> confirmed = hour to minute })
      }
    }

    compose.onNodeWithContentDescription("时").performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
    compose.onNodeWithContentDescription("分").performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
    compose.onNodeWithText("确定").performClick()

    assertEquals(0 to 0, confirmed)
  }
}
