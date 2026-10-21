package com.nu11b1ade.watermarkcam.domain

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class WatermarkTime(val clock: String, val date: String, val weekday: String)

/** Formatted effective values and size, shared by the live overlay and the burned-in text. */
data class WatermarkCard(
  val time: WatermarkTime? = null,
  val place: String? = null,
  val coordinates: String? = null,
  val weather: String? = null,
  val tag: String? = null,
  val watermarkSize: WatermarkSize = WatermarkSize.MEDIUM,
)

private val clockFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
private val dateFormat = DateTimeFormatter.ofPattern("uuuu-MM-dd", Locale.ROOT)

fun formatWatermarkTime(time: ZonedDateTime): WatermarkTime = WatermarkTime(
  clock = time.format(clockFormat),
  date = time.format(dateFormat),
  weekday = "星期" + "一二三四五六日"[time.dayOfWeek.value - 1],
)

fun formatWatermarkCoordinates(coordinates: Coordinates?): String = coordinates?.let {
  String.format(Locale.ROOT, "经度 %.4f° 纬度 %.4f°", it.longitude, it.latitude)
} ?: "经纬度 暂无"

fun formatWatermarkWeather(weather: WeatherReading?): String {
  if (weather == null || !weather.temperatureC.isFinite()) return "天气 暂无"
  val temperature = String.format(Locale.ROOT, "%.1f", weather.temperatureC).removeSuffix(".0")
  val condition = cleanWatermarkText(weather.condition).ifBlank { "天气" }
  return "$condition $temperature°C"
}

fun formatWatermarkCard(config: WatermarkConfig, effective: WatermarkContent): WatermarkCard = WatermarkCard(
  time = if (config.showTime) formatWatermarkTime(effective.capturedAt) else null,
  place = if (config.showPlace) cleanWatermarkText(effective.place).ifBlank { "地点 暂无" } else null,
  coordinates = if (config.showCoordinates && !config.hasUnlocatedPlaceOverride) formatWatermarkCoordinates(effective.coordinates) else null,
  weather = if (config.showWeather && !config.hasUnlocatedPlaceOverride) formatWatermarkWeather(effective.weather) else null,
  tag = if (config.showCustomText) cleanWatermarkText(config.customText).takeIf { it.isNotBlank() } else null,
  watermarkSize = config.watermarkSize,
)
