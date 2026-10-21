package com.nu11b1ade.watermarkcam.weather

import com.nu11b1ade.watermarkcam.domain.Coordinates
import com.nu11b1ade.watermarkcam.domain.WeatherReading
import org.json.JSONException
import org.json.JSONObject

fun parseOpenMeteoWeather(response: String, coordinates: Coordinates): WeatherReading? = try {
  val current = JSONObject(response).getJSONObject("current")
  val temperature = current.optDouble("temperature_2m", Double.NaN)
  if (temperature.isFinite()) WeatherReading(coordinates, temperature, briefCondition(current.optInt("weather_code", -1))) else null
} catch (_: JSONException) {
  null
}

private fun briefCondition(code: Int): String = when (code) {
  0 -> "晴"
  1 -> "晴间多云"
  2 -> "多云"
  3 -> "阴"
  45, 48 -> "雾"
  51, 53, 55 -> "毛毛雨"
  56, 57 -> "冻毛毛雨"
  61 -> "小雨"
  63 -> "中雨"
  65 -> "大雨"
  66, 67 -> "冻雨"
  71 -> "小雪"
  73 -> "中雪"
  75 -> "大雪"
  77 -> "雪粒"
  80, 81, 82 -> "阵雨"
  85, 86 -> "阵雪"
  95 -> "雷阵雨"
  96, 99 -> "雷阵雨伴冰雹"
  else -> "未知"
}
