package com.nu11b1ade.watermarkcam.history

import com.nu11b1ade.watermarkcam.domain.OverrideInput
import com.nu11b1ade.watermarkcam.domain.PreparedWatermark
import com.nu11b1ade.watermarkcam.domain.WATERMARK_TIME_FORMAT
import com.nu11b1ade.watermarkcam.domain.WatermarkConfig
import com.nu11b1ade.watermarkcam.domain.composeWatermark
import com.nu11b1ade.watermarkcam.domain.parseOverrides

/** Editor draft: validation and effective values are shared by live preview and save. */
data class PhotoEditState(
  val time: String,
  val place: String,
  val latitude: String,
  val longitude: String,
  val weatherText: String,
  val config: WatermarkConfig,
) {
  fun prepare(record: OriginalRecord): Result<PreparedWatermark> = parseOverrides(
    OverrideInput(true, time, true, place, latitude, longitude),
  ).map { overrides ->
    composeWatermark(config.copy(overrides = overrides, weatherText = weatherText), record.content).copy(frame = record.frame)
  }

  companion object {
    fun from(record: OriginalRecord): PhotoEditState {
      val effective = composeWatermark(record.config, record.content)
      return PhotoEditState(
        time = effective.content.capturedAt.format(WATERMARK_TIME_FORMAT), place = effective.content.place,
        latitude = effective.content.coordinates?.latitude?.toString().orEmpty(),
        longitude = effective.content.coordinates?.longitude?.toString().orEmpty(),
        weatherText = effective.card.weather.orEmpty(), config = record.config,
      )
    }
  }
}
