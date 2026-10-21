package com.nu11b1ade.watermarkcam

import com.nu11b1ade.watermarkcam.domain.clampOverrideDate
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class OverrideDateTest {
  @Test fun `February 29 becomes February 28 when changing to a non leap year`() {
    assertEquals(LocalDate.of(2025, 2, 28), clampOverrideDate(2025, 2, 29))
  }

  @Test fun `day 31 clamps to day 30 in a shorter month`() {
    assertEquals(LocalDate.of(2026, 4, 30), clampOverrideDate(2026, 4, 31))
  }

  @Test fun `February keeps day 29 in a leap year including a leap century`() {
    assertEquals(LocalDate.of(2024, 2, 29), clampOverrideDate(2024, 2, 31))
    assertEquals(LocalDate.of(2000, 2, 29), clampOverrideDate(2000, 2, 29))
    assertEquals(LocalDate.of(1900, 2, 28), clampOverrideDate(1900, 2, 29))
  }

  @Test fun `valid dates remain unchanged`() {
    assertEquals(LocalDate.of(2026, 10, 21), clampOverrideDate(2026, 10, 21))
    assertEquals(LocalDate.of(2026, 1, 31), clampOverrideDate(2026, 1, 31))
  }

  @Test fun `a date outside the supported years clamps the year and then the day`() {
    assertEquals(LocalDate.of(2025, 2, 28), clampOverrideDate(2024, 2, 29, 2025..2027))
    assertEquals(LocalDate.of(2027, 12, 31), clampOverrideDate(2030, 12, 31, 2021..2027))
  }
}
