package com.nu11b1ade.watermarkcam

import com.nu11b1ade.watermarkcam.domain.Coordinates
import com.nu11b1ade.watermarkcam.domain.OverrideState
import com.nu11b1ade.watermarkcam.domain.PlaceOverride
import com.nu11b1ade.watermarkcam.domain.WatermarkConfig
import com.nu11b1ade.watermarkcam.domain.WatermarkContent
import com.nu11b1ade.watermarkcam.domain.WeatherReading
import com.nu11b1ade.watermarkcam.domain.composeWatermark
import java.time.ZonedDateTime
import java.time.LocalDateTime
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WatermarkTest {
  @Test fun `place override without coordinates never leaks actual GPS or weather`() {
    val coordinates = Coordinates(31.2, 121.5)
    val observed = WatermarkContent(
      ZonedDateTime.parse("2026-10-21T09:08:07+08:00"), "上海", coordinates, WeatherReading(coordinates, 20.0, "晴"),
    )
    val config = WatermarkConfig(overrides = OverrideState(place = PlaceOverride("妈妈家")))
    val result = composeWatermark(config, observed)

    assertNull(result.content.coordinates)
    assertNull(result.content.weather)
    assertEquals(listOf("2026-10-21 09:08:07", "地点：妈妈家", "经纬度：--", "天气--", "今日打卡"), result.lines)
    assertEquals(observed, composeWatermark(WatermarkConfig(), observed).content)
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

  @Test fun `field toggles and whitespace edits affect only visible lines`() {
    val observed = WatermarkContent(ZonedDateTime.parse("2026-10-21T09:08:07+08:00"), "  公园\n  南门  ")
    val config = WatermarkConfig(
      showTime = false, showCoordinates = false, showWeather = false,
      customText = "  平安\n\n 喜乐  ",
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
        listOf("2026-10-21 09:08:07", "地点：上海市 黄浦区", "经纬度：31.230400, 121.473700", "天气：23.6°C 晴", "今日打卡"),
        composeWatermark(WatermarkConfig(), observed).lines,
      )
    } finally {
      Locale.setDefault(previousLocale)
    }
  }
}
