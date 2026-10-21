package com.nu11b1ade.watermarkcam

import com.nu11b1ade.watermarkcam.domain.Coordinates
import com.nu11b1ade.watermarkcam.domain.OverrideState
import com.nu11b1ade.watermarkcam.domain.PlaceOverride
import com.nu11b1ade.watermarkcam.domain.WatermarkCard
import com.nu11b1ade.watermarkcam.domain.WatermarkConfig
import com.nu11b1ade.watermarkcam.domain.WatermarkContent
import com.nu11b1ade.watermarkcam.domain.WatermarkTime
import com.nu11b1ade.watermarkcam.domain.WeatherReading
import com.nu11b1ade.watermarkcam.domain.composeWatermark
import com.nu11b1ade.watermarkcam.domain.formatWatermarkCard
import com.nu11b1ade.watermarkcam.domain.formatWatermarkCoordinates
import com.nu11b1ade.watermarkcam.domain.formatWatermarkTime
import com.nu11b1ade.watermarkcam.domain.formatWatermarkWeather
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class WatermarkFormattingTest {
  @Test fun `check-in time has a large clock and separate Chinese calendar labels`() {
    val time = formatWatermarkTime(ZonedDateTime.parse("2026-10-21T09:08:07+08:00"))

    assertEquals("09:08", time.clock)
    assertEquals("2026-10-21", time.date)
    assertEquals("星期三", time.weekday)
  }

  @Test fun `coordinates name longitude before latitude with four decimal places in any locale`() {
    val previous = Locale.getDefault()
    try {
      Locale.setDefault(Locale.GERMANY)
      assertEquals("经度 121.4737° 纬度 31.2304°", formatWatermarkCoordinates(Coordinates(31.2304, 121.4737)))
      assertEquals("经度 -70.6600° 纬度 -33.8600°", formatWatermarkCoordinates(Coordinates(-33.86, -70.66)))
      assertEquals("经纬度 暂无", formatWatermarkCoordinates(null))
    } finally {
      Locale.setDefault(previous)
    }
  }

  @Test fun `weather puts the condition first and avoids unnecessary decimal zeros`() {
    val coordinates = Coordinates(31.2304, 121.4737)
    assertEquals("晴间多云 17°C", formatWatermarkWeather(WeatherReading(coordinates, 17.0, "晴间多云")))
    assertEquals("小雪 -2.4°C", formatWatermarkWeather(WeatherReading(coordinates, -2.36, "  小雪\n ")))
    assertEquals("天气 暂无", formatWatermarkWeather(null))
    assertEquals("天气 暂无", formatWatermarkWeather(WeatherReading(coordinates, Double.NaN, "")))
  }

  @Test fun `card uses effective overrides and matching weather rather than the observed place`() {
    val coordinates = Coordinates(31.2304, 121.4737)
    val observed = WatermarkContent(
      ZonedDateTime.parse("2026-10-21T09:08:07+08:00"), "北京", Coordinates(39.9, 116.4),
      WeatherReading(coordinates, 17.0, "晴间多云"),
    )
    val card = composeWatermark(WatermarkConfig(
      showCustomText = true, customText = "  今日\n 打卡  ",
      overrides = OverrideState(LocalDateTime.parse("2024-05-06T07:08:09"), PlaceOverride("  上海\n 黄浦区  ", coordinates)),
    ), observed).card

    assertEquals("07:08", card.time?.clock)
    assertEquals("2024-05-06", card.time?.date)
    assertEquals("星期一", card.time?.weekday)
    assertEquals("上海 黄浦区", card.place)
    assertEquals("经度 121.4737° 纬度 31.2304°", card.coordinates)
    assertEquals("晴间多云 17°C", card.weather)
    assertEquals("今日 打卡", card.tag)
  }

  @Test fun `every watermark field can be shown independently or all hidden`() {
    val observed = WatermarkContent(ZonedDateTime.parse("2026-10-21T09:08:07+08:00"), "公园南门")
    val hidden = WatermarkConfig(
      showTime = false, showPlace = false, showCoordinates = false, showWeather = false, showCustomText = false,
    )
    val examples = listOf(
      hidden to WatermarkCard(),
      hidden.copy(showTime = true) to WatermarkCard(time = WatermarkTime("09:08", "2026-10-21", "星期三")),
      hidden.copy(showPlace = true) to WatermarkCard(place = "公园南门"),
      hidden.copy(showCoordinates = true) to WatermarkCard(coordinates = "经纬度 暂无"),
      hidden.copy(showWeather = true) to WatermarkCard(weather = "天气 暂无"),
      hidden.copy(showCustomText = true, customText = "平安喜乐") to WatermarkCard(tag = "平安喜乐"),
    )
    examples.forEach { (config, expected) -> assertEquals(expected, composeWatermark(config, observed).card) }
  }

  @Test fun `blank custom text and coordinates missing from a place override have no rows`() {
    val coordinates = Coordinates(31.2304, 121.4737)
    val observed = WatermarkContent(
      ZonedDateTime.parse("2026-10-21T09:08:07+08:00"), "", coordinates, WeatherReading(coordinates, 17.0, "晴"),
    )
    val card = composeWatermark(WatermarkConfig(
      showTime = false, showCustomText = true, customText = " \n ", overrides = OverrideState(place = PlaceOverride("家")),
    ), observed).card
    assertEquals(WatermarkCard(place = "家"), card)
    assertEquals("地点 暂无", composeWatermark(WatermarkConfig(), observed).card.place)
  }

  @Test fun `card formatting hides coordinate and weather rows for an override without coordinates`() {
    val effective = WatermarkContent(ZonedDateTime.parse("2026-10-21T09:08:07+08:00"), "陆家嘴")
    val config = WatermarkConfig(showTime = false, overrides = OverrideState(place = PlaceOverride("陆家嘴")))

    assertEquals(WatermarkCard(place = "陆家嘴"), formatWatermarkCard(config, effective))
  }

  @Test fun `Chinese weekdays include Sunday and the clock follows the effective local day`() {
    val monday = ZonedDateTime.parse("2026-10-19T00:00:00+08:00")
    val weekdays = (0L..6L).map { formatWatermarkTime(monday.plusDays(it)).weekday }
    assertEquals(listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"), weekdays)
    assertEquals(WatermarkTime("00:00", "2026-10-19", "星期一"), formatWatermarkTime(monday))
  }
}
