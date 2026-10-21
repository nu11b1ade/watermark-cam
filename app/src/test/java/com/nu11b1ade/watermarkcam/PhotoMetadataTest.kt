package com.nu11b1ade.watermarkcam

import com.nu11b1ade.watermarkcam.domain.Coordinates
import com.nu11b1ade.watermarkcam.domain.OverrideState
import com.nu11b1ade.watermarkcam.domain.PlaceOverride
import com.nu11b1ade.watermarkcam.domain.WatermarkConfig
import com.nu11b1ade.watermarkcam.domain.WatermarkContent
import com.nu11b1ade.watermarkcam.domain.composeWatermark
import com.nu11b1ade.watermarkcam.domain.photoMetadata
import java.time.LocalDateTime
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhotoMetadataTest {
  @Test fun `absent effective coordinates omit all GPS metadata and JSON escapes special text`() {
    val content = WatermarkContent(ZonedDateTime.parse("2026-10-21T09:08:07Z"), "家\"\\\n")
    val metadata = photoMetadata(content)
    assertNull(metadata.coordinates)
    assertNull(metadata.gpsDate)
    assertNull(metadata.gpsTime)
    assertEquals("+00:00", metadata.offset)
    assertEquals("{\"place\":\"\\u5bb6\\\"\\\\\\u000a\"}", metadata.userComment)
  }

  @Test fun `EXIF dates GPS time and place all come from effective overrides`() {
    val config = WatermarkConfig(overrides = OverrideState(
      time = LocalDateTime.parse("2025-01-02T03:04:05"),
      place = PlaceOverride("北京", Coordinates(39.9042, 116.4074)),
    ))
    val effective = composeWatermark(config, WatermarkContent(
      ZonedDateTime.parse("2026-10-21T09:08:07+08:00[Asia/Shanghai]"), "上海", Coordinates(31.2, 121.5),
    )).content
    val metadata = photoMetadata(effective)

    assertEquals("2025:01:02 03:04:05", metadata.dateTime)
    assertEquals("+08:00", metadata.offset)
    assertEquals("2025:01:01", metadata.gpsDate)
    assertEquals("19:04:05", metadata.gpsTime)
    assertEquals(Coordinates(39.9042, 116.4074), metadata.coordinates)
    assertEquals("{\"place\":\"\\u5317\\u4eac\"}", metadata.userComment)
  }
}
