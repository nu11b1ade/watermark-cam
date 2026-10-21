package com.nu11b1ade.watermarkcam.history

import com.nu11b1ade.watermarkcam.domain.Coordinates
import com.nu11b1ade.watermarkcam.domain.OverrideState
import com.nu11b1ade.watermarkcam.domain.PhotoFrame
import com.nu11b1ade.watermarkcam.domain.PlaceOverride
import com.nu11b1ade.watermarkcam.domain.WatermarkConfig
import com.nu11b1ade.watermarkcam.domain.WatermarkContent
import com.nu11b1ade.watermarkcam.domain.WatermarkSize
import com.nu11b1ade.watermarkcam.domain.WeatherReading
import java.time.LocalDateTime
import java.time.ZonedDateTime
import org.json.JSONObject

/** Immutable capture snapshot. createdAt is storage time, independent of the time override. */
data class OriginalRecord(
  val id: String,
  val createdAt: Long,
  val galleryUri: String,
  val content: WatermarkContent,
  val config: WatermarkConfig,
  val frame: PhotoFrame,
) {
  init { requireOriginalId(id) }
}

internal fun requireOriginalId(id: String) {
  require(id.matches(Regex("[A-Za-z0-9_-]{1,100}"))) { "无效的原图 ID" }
}

/** Pure sidecar serialization: no file access, Android context, or device time. */
fun encodeOriginalRecord(record: OriginalRecord): String = JSONObject().apply {
  put("id", record.id)
  put("createdAt", record.createdAt)
  put("galleryUri", record.galleryUri)
  put("content", JSONObject().apply {
    put("capturedAt", record.content.capturedAt.toString())
    put("place", record.content.place)
    put("coordinates", record.content.coordinates?.toJson() ?: JSONObject.NULL)
    put("weather", record.content.weather?.let { weather -> JSONObject().apply {
      put("coordinates", weather.coordinates.toJson())
      put("temperatureC", weather.temperatureC)
      put("condition", weather.condition)
    } } ?: JSONObject.NULL)
  })
  put("config", JSONObject().apply {
    val config = record.config
    put("showTime", config.showTime)
    put("showPlace", config.showPlace)
    put("showCoordinates", config.showCoordinates)
    put("showWeather", config.showWeather)
    put("showCustomText", config.showCustomText)
    put("customText", config.customText)
    put("watermarkSize", config.watermarkSize.name)
    put("weatherText", config.weatherText ?: JSONObject.NULL)
    put("overrides", JSONObject().apply {
      put("time", config.overrides.time?.toString() ?: JSONObject.NULL)
      put("place", config.overrides.place?.let { place -> JSONObject().apply {
        put("address", place.address)
        put("coordinates", place.coordinates?.toJson() ?: JSONObject.NULL)
      } } ?: JSONObject.NULL)
    })
  })
  put("frame", JSONObject().put("visibleHeightFraction", record.frame.visibleHeightFraction))
}.toString()

fun decodeOriginalRecord(json: String): OriginalRecord {
  val root = JSONObject(json)
  val content = root.getJSONObject("content")
  val config = root.getJSONObject("config")
  val overrides = config.getJSONObject("overrides")
  return OriginalRecord(
    id = root.getString("id"), createdAt = root.getLong("createdAt"), galleryUri = root.getString("galleryUri"),
    content = WatermarkContent(
      capturedAt = ZonedDateTime.parse(content.getString("capturedAt")), place = content.getString("place"),
      coordinates = content.optJSONObject("coordinates")?.toCoordinates(),
      weather = content.optJSONObject("weather")?.let {
        WeatherReading(it.getJSONObject("coordinates").toCoordinates(), it.getDouble("temperatureC"), it.getString("condition"))
      },
    ),
    config = WatermarkConfig(
      showTime = config.getBoolean("showTime"), showPlace = config.getBoolean("showPlace"),
      showCoordinates = config.getBoolean("showCoordinates"), showWeather = config.getBoolean("showWeather"),
      showCustomText = config.getBoolean("showCustomText"), customText = config.getString("customText"),
      watermarkSize = WatermarkSize.valueOf(config.getString("watermarkSize")),
      weatherText = if (config.isNull("weatherText")) null else config.getString("weatherText"),
      overrides = OverrideState(
        time = if (overrides.isNull("time")) null else LocalDateTime.parse(overrides.getString("time")),
        place = overrides.optJSONObject("place")?.let {
          PlaceOverride(it.getString("address"), it.optJSONObject("coordinates")?.toCoordinates())
        },
      ),
    ),
    frame = PhotoFrame(root.getJSONObject("frame").getDouble("visibleHeightFraction")),
  )
}

private fun Coordinates.toJson(): JSONObject = JSONObject().put("latitude", latitude).put("longitude", longitude)
private fun JSONObject.toCoordinates(): Coordinates = Coordinates(getDouble("latitude"), getDouble("longitude"))
