package com.nu11b1ade.watermarkcam.domain

import java.time.LocalDate
import java.time.YearMonth

fun clampOverrideDate(year: Int, month: Int, day: Int, yearRange: IntRange = 1..9999): LocalDate {
  val target = YearMonth.of(year.coerceIn(yearRange), month)
  return target.atDay(day.coerceIn(1, target.lengthOfMonth()))
}
