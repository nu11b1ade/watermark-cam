package com.nu11b1ade.watermarkcam

import com.nu11b1ade.watermarkcam.domain.Coordinates
import com.nu11b1ade.watermarkcam.ui.PlaceLookupTrigger
import com.nu11b1ade.watermarkcam.ui.placeCoordinatesAfterLookup
import com.nu11b1ade.watermarkcam.location.PlaceCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceCoordinatesRulesTest {
  private val first = PlaceCandidate("上海市 浦东新区 陆家嘴", "陆家嘴, 浦东新区, 上海市, 中国", Coordinates(31.2397, 121.4998))
  private val second = PlaceCandidate("上海市 浦东新区 东方明珠", "东方明珠, 浦东新区, 上海市, 中国", Coordinates(31.2396, 121.4997))

  @Test fun `debounced lookup fills the first candidate only until coordinates are manually edited`() {
    val automatic = placeCoordinatesAfterLookup(PlaceLookupTrigger.DEBOUNCE, false, listOf(first, second))
    assertEquals(first.coordinates, automatic.coordinatesToFill)
    assertFalse(automatic.manuallyEdited)

    val protected = placeCoordinatesAfterLookup(PlaceLookupTrigger.DEBOUNCE, true, listOf(first, second))
    assertNull(protected.coordinatesToFill)
    assertTrue(protected.manuallyEdited)
  }

  @Test fun `button and pick explicitly replace coordinates and clear manual protection`() {
    listOf(PlaceLookupTrigger.BUTTON, PlaceLookupTrigger.PICK).forEach { trigger ->
      val result = placeCoordinatesAfterLookup(trigger, true, listOf(second))
      assertEquals(second.coordinates, result.coordinatesToFill)
      assertFalse(result.manuallyEdited)
    }
  }

  @Test fun `no candidates leave coordinates alone and only explicit actions clear manual protection`() {
    PlaceLookupTrigger.entries.forEach { trigger ->
      val result = placeCoordinatesAfterLookup(trigger, true, emptyList())
      assertNull(result.coordinatesToFill)
      assertEquals(trigger == PlaceLookupTrigger.DEBOUNCE, result.manuallyEdited)
    }
  }
}
