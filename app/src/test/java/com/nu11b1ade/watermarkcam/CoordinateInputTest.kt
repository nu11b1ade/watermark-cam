package com.nu11b1ade.watermarkcam

import com.nu11b1ade.watermarkcam.domain.Coordinates
import com.nu11b1ade.watermarkcam.domain.OverrideInput
import com.nu11b1ade.watermarkcam.domain.parseOverrides
import com.nu11b1ade.watermarkcam.domain.toggleCoordinateSign
import org.junit.Assert.assertEquals
import org.junit.Test

class CoordinateInputTest {
  @Test fun `sign button preserves decimal drafts and permits southern and western overrides`() {
    val examples = listOf("31.2304" to "-31.2304", " -33.8600 " to "33.8600", "+121.47" to "-121.47",
      "" to "-", "-" to "", "-0." to "0.")
    examples.forEach { (input, expected) -> assertEquals(expected, toggleCoordinateSign(input)) }
    val overrides = parseOverrides(OverrideInput(
      placeEnabled = true, place = "手动地点", latitude = toggleCoordinateSign("33.86"), longitude = toggleCoordinateSign("70.66"),
    )).getOrThrow()
    assertEquals(Coordinates(-33.86, -70.66), overrides.place?.coordinates)
  }
}
