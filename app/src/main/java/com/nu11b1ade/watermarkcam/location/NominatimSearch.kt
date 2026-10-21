package com.nu11b1ade.watermarkcam.location

import com.nu11b1ade.watermarkcam.domain.Coordinates
import com.nu11b1ade.watermarkcam.domain.cleanWatermarkText
import org.json.JSONArray
import org.json.JSONException

/** Nominatim lists the most specific part first; show up to three parts from general to specific. */
internal fun shortenPlaceName(fullName: String): String = cleanWatermarkText(
  fullName.split(',', '，').map(String::trim)
    .filter { it.isNotBlank() && !it.all(Char::isDigit) && it !in setOf("中国", "China", "中华人民共和国") }
    .distinct().take(3).asReversed().joinToString(" "),
)

fun parseNominatimSearch(json: String): List<PlaceCandidate> = try {
  val entries = JSONArray(json)
  buildList {
    for (index in 0 until entries.length()) {
      val entry = entries.optJSONObject(index) ?: continue
      val fullName = (entry.opt("display_name") as? String)?.trim()?.takeIf { it.isNotBlank() } ?: continue
      val name = shortenPlaceName(fullName).takeIf { it.isNotBlank() } ?: continue
      val latitude = entry.optString("lat").toDoubleOrNull() ?: continue
      val longitude = entry.optString("lon").toDoubleOrNull() ?: continue
      val coordinates = try {
        Coordinates(latitude, longitude)
      } catch (_: IllegalArgumentException) {
        continue
      }
      add(PlaceCandidate(name, fullName, coordinates))
    }
  }
} catch (_: JSONException) {
  emptyList()
}
