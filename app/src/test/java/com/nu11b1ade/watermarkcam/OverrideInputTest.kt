package com.nu11b1ade.watermarkcam

import com.nu11b1ade.watermarkcam.domain.OverrideInput
import com.nu11b1ade.watermarkcam.domain.OverrideState
import com.nu11b1ade.watermarkcam.domain.PlaceOverride
import com.nu11b1ade.watermarkcam.domain.Coordinates
import com.nu11b1ade.watermarkcam.domain.parseOverrides
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverrideInputTest {
  @Test fun `incomplete nonfinite and out of range coordinates are rejected`() {
    listOf("31.2" to "", "" to "121.5", "NaN" to "121", "91" to "121", "31" to "181", "Infinity" to "0")
      .forEach { (latitude, longitude) ->
        assertTrue("$latitude, $longitude", parseOverrides(OverrideInput(
          placeEnabled = true, place = "公园", latitude = latitude, longitude = longitude,
        )).isFailure)
      }
  }

  @Test fun `valid leap day is accepted and disabled edits do not take effect`() {
    assertEquals(
      OverrideState(time = LocalDateTime.parse("2024-02-29T12:00:00")),
      parseOverrides(OverrideInput(timeEnabled = true, time = "2024-02-29 12:00:00")).getOrThrow(),
    )
    assertEquals(OverrideState(), parseOverrides(OverrideInput(time = "bad", place = "", latitude = "NaN")).getOrThrow())
    assertTrue(parseOverrides(OverrideInput(placeEnabled = true)).isFailure)
    assertEquals(OverrideState(place = PlaceOverride("家")), parseOverrides(OverrideInput(placeEnabled = true, place = "家")).getOrThrow())
  }

  @Test fun `place override accepts a complete optional coordinate pair`() {
    assertEquals(
      OverrideState(place = PlaceOverride("公园", Coordinates(-31.2, 121.5))),
      parseOverrides(OverrideInput(placeEnabled = true, place = " 公园 ", latitude = "-31.2", longitude = "121.5")).getOrThrow(),
    )
  }

  @Test fun `invalid calendar date is rejected rather than silently changed`() {
    val result = parseOverrides(OverrideInput(timeEnabled = true, time = "2026-02-30 12:00:00"))
    assertTrue(result.isFailure)
  }
}
