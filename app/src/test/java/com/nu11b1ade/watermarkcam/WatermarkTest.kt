package com.nu11b1ade.watermarkcam

import com.nu11b1ade.watermarkcam.domain.Coordinates
import com.nu11b1ade.watermarkcam.domain.OverrideState
import com.nu11b1ade.watermarkcam.domain.PlaceOverride
import com.nu11b1ade.watermarkcam.domain.WatermarkConfig
import com.nu11b1ade.watermarkcam.domain.WatermarkContent
import com.nu11b1ade.watermarkcam.domain.WeatherReading
import com.nu11b1ade.watermarkcam.domain.composeWatermark
import com.nu11b1ade.watermarkcam.domain.effectiveCoordinates
import java.time.ZonedDateTime
import java.time.LocalDateTime
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class WatermarkTest {
  @Test fun `default config hides the coordinate line but retains coordinates and weather`() {
    val config = WatermarkConfig()
    val coords = Coordinates(31.2, 121.5)
    val observed = WatermarkContent(ZonedDateTime.parse("2026-10-21T09:08:07+08:00"), "上海", coords, WeatherReading(coords, 23.0, "多云"))
    val watermark = composeWatermark(config, observed)

    assertFalse(config.showCoordinates)
    assertNull(watermark.card.coordinates)
    assertEquals(observed, watermark.content)
    assertEquals("多云 23°C", watermark.card.weather)
    assertFalse(config.showCustomText)
    assertEquals("", config.customText)
    assertNull(watermark.card.tag)
    assertEquals(3, watermark.lines.size)
  }

  @Test fun `place override without coordinates never leaks actual GPS or weather`() {
    val coordinates = Coordinates(31.2, 121.5)
    val observed = WatermarkContent(
      ZonedDateTime.parse("2026-10-21T09:08:07+08:00"), "上海", coordinates, WeatherReading(coordinates, 20.0, "晴"),
    )
    val config = WatermarkConfig(overrides = OverrideState(place = PlaceOverride("妈妈家")))
    val result = composeWatermark(config, observed)

    assertNull(result.content.coordinates)
    assertNull(result.content.weather)
    assertEquals(listOf("2026-10-21 09:08:07", "地点：妈妈家"), result.lines)
    assertNull(result.card.coordinates)
    assertNull(result.card.weather)
    assertEquals(observed, composeWatermark(WatermarkConfig(), observed).content)
  }

  @Test fun `effective coordinates use override coordinates only while place override is enabled`() {
    val observed = WatermarkContent(ZonedDateTime.parse("2026-10-21T09:08:07+08:00"), "当前位置", Coordinates(39.9, 116.4))
    val manual = Coordinates(31.2397, 121.4998)

    assertEquals(manual, effectiveCoordinates(WatermarkConfig(overrides = OverrideState(place = PlaceOverride("陆家嘴", manual))), observed))
    assertEquals(observed.coordinates, effectiveCoordinates(WatermarkConfig(), observed))
    assertNull(effectiveCoordinates(WatermarkConfig(overrides = OverrideState(place = PlaceOverride("陆家嘴"))), observed))
  }

  @Test fun `automatic location keeps coordinate and weather placeholders when unavailable`() {
    val observed = WatermarkContent(ZonedDateTime.parse("2026-10-21T09:08:07+08:00"), "位置暂不可用")
    val result = composeWatermark(WatermarkConfig(showCoordinates = true), observed)

    assertEquals(listOf("2026-10-21 09:08:07", "地点：位置暂不可用", "经纬度：--", "天气--"), result.lines)
    assertEquals("经纬度 暂无", result.card.coordinates)
    assertEquals("天气 暂无", result.card.weather)
  }

  @Test fun `weather at manual coordinates is retained and all visible fields can be disabled`() {
    val coordinates = Coordinates(-33.86, 151.21)
    val observed = WatermarkContent(
      ZonedDateTime.parse("2026-10-21T09:08:07Z"), "位置暂不可用", null, WeatherReading(coordinates, 19.0, "多云"),
    )
    val config = WatermarkConfig(
      showTime = false, showPlace = false, showCoordinates = false, showWeather = false, showCustomText = false,
      overrides = OverrideState(place = PlaceOverride("悉尼", coordinates)),
    )
    val result = composeWatermark(config, observed)
    assertEquals(emptyList<String>(), result.lines)
    assertEquals(observed.weather, result.content.weather)
  }

  @Test fun `explicit weather text is verbatim even at an unlocated place while blank or disabled hides it`() {
    val observed = WatermarkContent(ZonedDateTime.parse("2026-10-21T09:08:07+08:00"))
    val config = WatermarkConfig(weatherText = "多云 23°C", overrides = OverrideState(place = PlaceOverride("妈妈家")))
    val result = composeWatermark(config, observed)
    assertEquals("多云 23°C", result.card.weather)
    assertEquals("多云 23°C", result.lines.last())
    assertNull(result.content.coordinates)
    assertNull(composeWatermark(config.copy(weatherText = ""), observed).card.weather)
    assertEquals(2, composeWatermark(config.copy(weatherText = ""), observed).lines.size)
    assertNull(composeWatermark(config.copy(showWeather = false), observed).card.weather)
  }

  @Test fun `field toggles and whitespace edits affect only visible lines`() {
    val observed = WatermarkContent(ZonedDateTime.parse("2026-10-21T09:08:07+08:00"), "  公园\n  南门  ")
    val config = WatermarkConfig(
      showTime = false, showCoordinates = false, showWeather = false,
      showCustomText = true, customText = "  平安\n\n 喜乐  ",
    )
    assertEquals(listOf("地点：公园 南门", "平安 喜乐"), composeWatermark(config, observed).lines)
  }

  @Test fun `time and place overrides select the same effective content for photo and metadata`() {
    val actualCoordinates = Coordinates(31.2304, 121.4737)
    val fakeCoordinates = Coordinates(39.9042, 116.4074)
    val observed = WatermarkContent(
      ZonedDateTime.parse("2026-10-21T09:08:07+08:00[Asia/Shanghai]"),
      "上海", actualCoordinates, WeatherReading(actualCoordinates, 23.6, "晴"),
    )
    val config = WatermarkConfig(overrides = OverrideState(
      time = LocalDateTime.parse("2025-01-02T03:04:05"),
      place = PlaceOverride("北京", fakeCoordinates),
    ))

    assertEquals(
      WatermarkContent(ZonedDateTime.parse("2025-01-02T03:04:05+08:00[Asia/Shanghai]"), "北京", fakeCoordinates),
      composeWatermark(config, observed).content,
    )
  }

  @Test fun `Chinese watermark has stable field order and locale independent numbers`() {
    val previousLocale = Locale.getDefault()
    try {
      Locale.setDefault(Locale.GERMANY)
      val coordinates = Coordinates(31.2304, 121.4737)
      val observed = WatermarkContent(
        capturedAt = ZonedDateTime.parse("2026-10-21T09:08:07+08:00[Asia/Shanghai]"),
        place = "上海市 黄浦区",
        coordinates = coordinates,
        weather = WeatherReading(coordinates, 23.6, "晴"),
      )

      assertEquals(
        listOf("2026-10-21 09:08:07", "地点：上海市 黄浦区", "经纬度：31.230400, 121.473700", "天气：23.6°C 晴"),
        composeWatermark(WatermarkConfig(showCoordinates = true), observed).lines,
      )
    } finally {
      Locale.setDefault(previousLocale)
    }
  }
}
