package com.nu11b1ade.watermarkcam.domain

import java.time.ZonedDateTime
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

const val MAX_WATERMARK_TEXT_LENGTH = 120

data class Coordinates(val latitude: Double, val longitude: Double) {
  init {
    require(latitude.isFinite() && latitude in -90.0..90.0) { "纬度应在 -90 到 90 之间" }
    require(longitude.isFinite() && longitude in -180.0..180.0) { "经度应在 -180 到 180 之间" }
  }
}

data class WeatherReading(val coordinates: Coordinates, val temperatureC: Double, val condition: String)

data class PlaceOverride(val address: String, val coordinates: Coordinates? = null)

data class OverrideState(val time: LocalDateTime? = null, val place: PlaceOverride? = null)

data class WatermarkConfig(
  val showTime: Boolean = true,
  val showPlace: Boolean = true,
  val showCoordinates: Boolean = true,
  val showWeather: Boolean = true,
  val showCustomText: Boolean = false,
  val customText: String = "",
  val overrides: OverrideState = OverrideState(),
  val watermarkSize: WatermarkSize = WatermarkSize.MEDIUM,
)

data class WatermarkContent(
  val capturedAt: ZonedDateTime,
  val place: String = "位置获取中",
  val coordinates: Coordinates? = null,
  val weather: WeatherReading? = null,
)

data class PreparedWatermark(
  val content: WatermarkContent,
  val lines: List<String>,
  val card: WatermarkCard,
  val frame: PhotoFrame = PhotoFrame(),
)

val WATERMARK_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss", Locale.ROOT)

fun cleanWatermarkText(value: String): String = value.trim().replace(Regex("\\s+"), " ").take(MAX_WATERMARK_TEXT_LENGTH)

fun composeWatermark(config: WatermarkConfig, observed: WatermarkContent): PreparedWatermark {
  val coordinates = if (config.overrides.place != null) config.overrides.place.coordinates else observed.coordinates
  val effective = observed.copy(
    capturedAt = config.overrides.time?.atZone(observed.capturedAt.zone) ?: observed.capturedAt,
    place = cleanWatermarkText(config.overrides.place?.address ?: observed.place),
    coordinates = coordinates,
    weather = observed.weather?.takeIf { it.coordinates == coordinates },
  )
  val lines = buildList {
    if (config.showTime) add(effective.capturedAt.format(WATERMARK_TIME_FORMAT))
    if (config.showPlace) add("地点：${effective.place}")
    if (config.showCoordinates) add(effective.coordinates?.let {
      String.format(Locale.ROOT, "经纬度：%.6f, %.6f", it.latitude, it.longitude)
    } ?: "经纬度：--")
    if (config.showWeather) add(effective.weather?.let {
      String.format(Locale.ROOT, "天气：%.1f°C %s", it.temperatureC, it.condition)
    } ?: "天气--")
    if (config.showCustomText && config.customText.isNotBlank()) add(cleanWatermarkText(config.customText))
  }
  return PreparedWatermark(effective, lines, formatWatermarkCard(config, effective))
}
