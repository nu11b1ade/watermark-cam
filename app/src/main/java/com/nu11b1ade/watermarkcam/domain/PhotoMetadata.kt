package com.nu11b1ade.watermarkcam.domain

import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

data class PhotoMetadata(
  val dateTime: String,
  val offset: String,
  val coordinates: Coordinates?,
  val gpsDate: String?,
  val gpsTime: String?,
  val userComment: String,
)

fun photoMetadata(content: WatermarkContent): PhotoMetadata {
  val utc = content.capturedAt.withZoneSameInstant(ZoneOffset.UTC)
  return PhotoMetadata(
    dateTime = content.capturedAt.format(DateTimeFormatter.ofPattern("uuuu:MM:dd HH:mm:ss", Locale.ROOT)),
    offset = content.capturedAt.format(DateTimeFormatter.ofPattern("xxx", Locale.ROOT)),
    coordinates = content.coordinates,
    gpsDate = content.coordinates?.let { utc.format(DateTimeFormatter.ofPattern("uuuu:MM:dd", Locale.ROOT)) },
    gpsTime = content.coordinates?.let { utc.format(DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT)) },
    // EXIF UserComment support varies between gallery apps. ASCII-safe JSON retains Chinese losslessly.
    userComment = "{\"place\":\"${escapeJson(content.place)}\"}",
  )
}

private fun escapeJson(value: String): String = buildString {
  value.forEach { char ->
    when (char) {
      '"' -> append("\\\"")
      '\\' -> append("\\\\")
      in ' '..'~' -> append(char)
      else -> append(String.format(Locale.ROOT, "\\u%04x", char.code))
    }
  }
}
