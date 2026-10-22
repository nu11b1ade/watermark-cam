package com.nu11b1ade.watermarkcam.data

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import com.nu11b1ade.watermarkcam.domain.Coordinates
import com.nu11b1ade.watermarkcam.domain.OverrideInput
import com.nu11b1ade.watermarkcam.domain.OverrideState
import com.nu11b1ade.watermarkcam.domain.MAX_WATERMARK_TEXT_LENGTH
import com.nu11b1ade.watermarkcam.domain.WATERMARK_TIME_FORMAT
import com.nu11b1ade.watermarkcam.domain.WatermarkConfig
import com.nu11b1ade.watermarkcam.domain.WatermarkSize
import com.nu11b1ade.watermarkcam.domain.cleanWatermarkText
import com.nu11b1ade.watermarkcam.domain.parseOverrides
import com.nu11b1ade.watermarkcam.location.LocationReading

class SettingsStore(context: Context) {
  private val preferences = context.getSharedPreferences("watermark", Context.MODE_PRIVATE)

  /** null means the first-launch notice has not been answered. */
  fun loadAMapConsent(): Boolean? =
    if (preferences.contains("amapConsent")) preferences.getBoolean("amapConsent", false) else null

  fun saveAMapConsent(agreed: Boolean) {
    preferences.edit(commit = true) { putBoolean("amapConsent", agreed) }
  }

  /** Display cache for automatic location only; never reads or changes the location override. */
  fun loadLastLocation(): LocationReading? = runCatching {
    val place = preferences.getString("lastAutoPlace", null)?.let(::cleanWatermarkText)
      ?.takeIf { it.isNotBlank() } ?: return null
    val latitude = preferences.getString("lastAutoLatitude", null)?.toDoubleOrNull() ?: return null
    val longitude = preferences.getString("lastAutoLongitude", null)?.toDoubleOrNull() ?: return null
    LocationReading(place, Coordinates(latitude, longitude))
  }.getOrNull()

  fun saveLastLocation(place: String, coordinates: Coordinates) {
    val address = cleanWatermarkText(place)
    require(address.isNotBlank())
    preferences.edit(commit = true) {
      putString("lastAutoPlace", address)
      putString("lastAutoLatitude", coordinates.latitude.toString())
      putString("lastAutoLongitude", coordinates.longitude.toString())
    }
  }

  fun loadLastSavedUri(): Uri? =
    preferences.getString("lastSavedUri", null)?.takeIf { it.isNotBlank() }?.let(Uri::parse)

  fun saveLastSavedUri(uri: Uri?) {
    preferences.edit(commit = true) {
      if (uri == null) remove("lastSavedUri") else putString("lastSavedUri", uri.toString())
    }
  }

  fun loadRetentionDays(): Int? = when (val days = preferences.getInt("originalRetentionDays", 7)) {
    -1 -> null
    7, 30, 90 -> days
    else -> 7
  }

  fun saveRetentionDays(days: Int?) {
    require(days == null || days in listOf(7, 30, 90))
    preferences.edit(commit = true) { putInt("originalRetentionDays", days ?: -1) }
  }

  fun load(): WatermarkConfig {
    val defaults = WatermarkConfig()
    val overrides = parseOverrides(OverrideInput(
      timeEnabled = preferences.getBoolean("overrideTime", false),
      time = preferences.getString("time", "").orEmpty(),
      placeEnabled = preferences.getBoolean("overridePlace", false),
      place = preferences.getString("place", "").orEmpty(),
      latitude = preferences.getString("latitude", "").orEmpty(),
      longitude = preferences.getString("longitude", "").orEmpty(),
    )).getOrDefault(OverrideState())
    return WatermarkConfig(
      showTime = preferences.getBoolean("showTime", defaults.showTime),
      showPlace = preferences.getBoolean("showPlace", defaults.showPlace),
      showCoordinates = preferences.getBoolean("showCoordinates", defaults.showCoordinates),
      showWeather = preferences.getBoolean("showWeather", defaults.showWeather),
      showCustomText = preferences.getBoolean("showCustomText", defaults.showCustomText),
      customText = preferences.getString("customText", defaults.customText).orEmpty().take(MAX_WATERMARK_TEXT_LENGTH),
      overrides = overrides,
      watermarkSize = WatermarkSize.entries.find { it.name == preferences.getString("watermarkSize", null) }
        ?: defaults.watermarkSize,
    )
  }

  fun save(config: WatermarkConfig) {
    preferences.edit(commit = true) {
      putBoolean("showTime", config.showTime)
      putBoolean("showPlace", config.showPlace)
      putBoolean("showCoordinates", config.showCoordinates)
      putBoolean("showWeather", config.showWeather)
      putBoolean("showCustomText", config.showCustomText)
      putString("customText", config.customText)
      putString("watermarkSize", config.watermarkSize.name)
      putBoolean("overrideTime", config.overrides.time != null)
      putString("time", config.overrides.time?.format(WATERMARK_TIME_FORMAT))
      putBoolean("overridePlace", config.overrides.place != null)
      putString("place", config.overrides.place?.address)
      putString("latitude", config.overrides.place?.coordinates?.latitude?.toString())
      putString("longitude", config.overrides.place?.coordinates?.longitude?.toString())
    }
  }
}
