package com.nu11b1ade.watermarkcam.location

import com.nu11b1ade.watermarkcam.domain.cleanWatermarkText
import org.json.JSONException
import org.json.JSONObject

fun parseNominatimAddress(response: String): String? {
  return try {
    val json = JSONObject(response)
    if (json.has("error")) return null
    val displayName = json.opt("display_name") as? String ?: return null
    val address = json.optJSONObject("address")
    val country = address?.optString("country")
    val countryCode = address?.optString("country_code").orEmpty()
    val separator = if (countryCode.isBlank() || countryCode.equals("cn", ignoreCase = true)) "" else " "
    cleanWatermarkText(displayName.split(',').map(String::trim).asReversed()
      .filter { it.isNotBlank() && !it.all(Char::isDigit) && it != country && it !in setOf("中国", "China", "中华人民共和国") }
      .distinct().joinToString(separator)).ifBlank { null }
  } catch (_: JSONException) {
    null
  }
}
