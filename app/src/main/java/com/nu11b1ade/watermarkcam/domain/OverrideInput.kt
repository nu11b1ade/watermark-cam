package com.nu11b1ade.watermarkcam.domain

import java.time.LocalDateTime
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle

data class OverrideInput(
  val timeEnabled: Boolean = false,
  val time: String = "",
  val placeEnabled: Boolean = false,
  val place: String = "",
  val latitude: String = "",
  val longitude: String = "",
)

fun parseOverrides(input: OverrideInput): Result<OverrideState> = runCatching {
  val time = if (input.timeEnabled) {
    try {
      LocalDateTime.parse(input.time.trim(), WATERMARK_TIME_FORMAT.withResolverStyle(ResolverStyle.STRICT))
        .also { require(it.year in 1..9999) { "年份应在 0001 到 9999 之间" } }
    } catch (_: DateTimeParseException) {
      throw IllegalArgumentException("请输入有效时间，格式：2026-10-21 09:08:07")
    }
  } else null
  val place = if (input.placeEnabled) {
    require(input.place.isNotBlank()) { "开启覆盖地点时，请填写地点" }
    require(input.place.length <= MAX_WATERMARK_TEXT_LENGTH) { "地点最多 $MAX_WATERMARK_TEXT_LENGTH 字" }
    val latitude = input.latitude.trim()
    val longitude = input.longitude.trim()
    val coordinates = if (latitude.isEmpty() && longitude.isEmpty()) null else {
      val lat = latitude.toDoubleOrNull()
      val lng = longitude.toDoubleOrNull()
      require(lat != null && lng != null) { "请同时填写有效纬度和经度，或同时留空" }
      Coordinates(lat, lng)
    }
    PlaceOverride(cleanWatermarkText(input.place), coordinates)
  } else null
  OverrideState(time, place)
}
