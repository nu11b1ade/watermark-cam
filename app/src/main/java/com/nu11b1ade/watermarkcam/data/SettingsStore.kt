package com.nu11b1ade.watermarkcam.data

import android.content.Context
import androidx.core.content.edit
import com.nu11b1ade.watermarkcam.domain.OverrideInput
import com.nu11b1ade.watermarkcam.domain.OverrideState
import com.nu11b1ade.watermarkcam.domain.MAX_WATERMARK_TEXT_LENGTH
import com.nu11b1ade.watermarkcam.domain.WATERMARK_TIME_FORMAT
import com.nu11b1ade.watermarkcam.domain.WatermarkConfig
import com.nu11b1ade.watermarkcam.domain.WatermarkSize
import com.nu11b1ade.watermarkcam.domain.parseOverrides

class SettingsStore(context: Context) {
  private val preferences = context.getSharedPreferences("watermark", Context.MODE_PRIVATE)

  /** null means the first-launch notice has not been answered. */
  fun loadAMapConsent(): Boolean? =
    if (preferences.contains("amapConsent")) preferences.getBoolean("amapConsent", false) else null

  fun saveAMapConsent(agreed: Boolean) {
    preferences.edit { putBoolean("amapConsent", agreed) }
  }

  fun loadRetentionDays(): Int? = when (val days = preferences.getInt("originalRetentionDays", 7)) {
    -1 -> null
    7, 30, 90 -> days
    else -> 7
  }

  fun saveRetentionDays(days: Int?) {
    require(days == null || days in listOf(7, 30, 90))
    preferences.edit { putInt("originalRetentionDays", days ?: -1) }
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
    preferences.edit {
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
