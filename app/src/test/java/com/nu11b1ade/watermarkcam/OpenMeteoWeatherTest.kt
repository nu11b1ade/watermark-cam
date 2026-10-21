package com.nu11b1ade.watermarkcam

import com.nu11b1ade.watermarkcam.domain.Coordinates
import com.nu11b1ade.watermarkcam.domain.WeatherReading
import com.nu11b1ade.watermarkcam.weather.parseOpenMeteoWeather
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OpenMeteoWeatherTest {
  @Test fun `real current weather response gives temperature and Chinese condition`() {
    val coordinates = Coordinates(31.2304, 121.4737)
    val response = """{"current":{"time":"2026-10-21T11:00","interval":900,"temperature_2m":17.4,"weather_code":1}}"""

    assertEquals(WeatherReading(coordinates, 17.4, "晴间多云"), parseOpenMeteoWeather(response, coordinates))
  }

  @Test fun `WMO codes distinguish Chinese precipitation intensity and thunderstorms with hail`() {
    val conditions = mapOf(
      0 to "晴", 1 to "晴间多云", 2 to "多云", 3 to "阴", 45 to "雾", 48 to "雾",
      51 to "毛毛雨", 53 to "毛毛雨", 55 to "毛毛雨", 56 to "冻毛毛雨", 57 to "冻毛毛雨",
      61 to "小雨", 63 to "中雨", 65 to "大雨", 66 to "冻雨", 67 to "冻雨",
      71 to "小雪", 73 to "中雪", 75 to "大雪", 77 to "雪粒",
      80 to "阵雨", 81 to "阵雨", 82 to "阵雨", 85 to "阵雪", 86 to "阵雪",
      95 to "雷阵雨", 96 to "雷阵雨伴冰雹", 99 to "雷阵雨伴冰雹", 123 to "未知",
    )
    conditions.forEach { (code, expected) ->
      val response = """{"current":{"temperature_2m":-2.5,"weather_code":$code}}"""
      assertEquals("WMO $code", expected, parseOpenMeteoWeather(response, Coordinates(31.0, 121.0))?.condition)
    }
  }

  @Test fun `missing or invalid temperature and malformed weather responses are unavailable`() {
    listOf("not json", "{}", """{"current":{}}""", """{"current":{"temperature_2m":null}}""",
      """{"current":{"temperature_2m":"NaN"}}""", """{"current":{"temperature_2m":"Infinity"}}""",
    ).forEach { assertNull(it, parseOpenMeteoWeather(it, Coordinates(31.0, 121.0))) }
    assertEquals("未知", parseOpenMeteoWeather("""{"current":{"temperature_2m":0}}""", Coordinates(31.0, 121.0))?.condition)
  }
}
