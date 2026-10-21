package com.nu11b1ade.watermarkcam

import com.nu11b1ade.watermarkcam.domain.Coordinates
import com.nu11b1ade.watermarkcam.domain.OverrideState
import com.nu11b1ade.watermarkcam.domain.PhotoFrame
import com.nu11b1ade.watermarkcam.domain.PlaceOverride
import com.nu11b1ade.watermarkcam.domain.WatermarkConfig
import com.nu11b1ade.watermarkcam.domain.WatermarkContent
import com.nu11b1ade.watermarkcam.domain.WatermarkSize
import com.nu11b1ade.watermarkcam.domain.WeatherReading
import com.nu11b1ade.watermarkcam.history.OriginalRecord
import com.nu11b1ade.watermarkcam.history.decodeOriginalRecord
import com.nu11b1ade.watermarkcam.history.encodeOriginalRecord
import java.time.LocalDateTime
import java.time.ZonedDateTime
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OriginalRecordJsonTest {
  @Test fun `sidecar round trip retains zoned time weather rendering flags overrides and frame`() {
    val coords = Coordinates(-33.86, 151.21)
    val record = OriginalRecord(
      id = "photo-1", createdAt = 1792544887000L, galleryUri = "content://media/external/images/media/42",
      content = WatermarkContent(ZonedDateTime.parse("2026-10-21T09:08:07+08:00[Asia/Shanghai]"),
        "公园 \"南门\"", coords, WeatherReading(coords, 23.5, "多云")),
      config = WatermarkConfig(showTime = false, showPlace = false, showCoordinates = true, showWeather = false,
        showCustomText = true, customText = "平安\n喜乐", watermarkSize = WatermarkSize.XLARGE, weatherText = "多云 23°C",
        overrides = OverrideState(LocalDateTime.parse("2025-01-02T03:04:05"), PlaceOverride("悉尼", coords))),
      frame = PhotoFrame(0.82),
    )

    val json = encodeOriginalRecord(record)
    assertEquals("2026-10-21T09:08:07+08:00[Asia/Shanghai]", JSONObject(json).getJSONObject("content").getString("capturedAt"))
    assertEquals(1792544887000L, JSONObject(json).getLong("createdAt"))
    assertEquals(record, decodeOriginalRecord(json))
  }

  @Test fun `sidecar round trip retains missing coordinates weather and overrides`() {
    val record = OriginalRecord("empty", 0L, "content://photos/1",
      WatermarkContent(ZonedDateTime.parse("2026-10-21T09:08:07Z")), WatermarkConfig(), PhotoFrame())
    assertEquals(record, decodeOriginalRecord(encodeOriginalRecord(record)))
  }
}
