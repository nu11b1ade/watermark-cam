package com.nu11b1ade.watermarkcam.location

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NominatimForwardGeocoder(
  private val openConnection: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
) : ForwardGeocoder {
  private val cache = object : LinkedHashMap<String, List<PlaceCandidate>>(32, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<PlaceCandidate>>): Boolean = size > 32
  }

  override suspend fun search(query: String): List<PlaceCandidate> = searchResult(query).getOrDefault(emptyList())

  override suspend fun searchResult(query: String): Result<List<PlaceCandidate>> = forwardSearchResult {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) emptyList() else withContext(Dispatchers.IO) {
      NominatimThrottle.run(
        cached = { cache[trimmed] },
        request = { request(trimmed).also { cache[trimmed] = it } },
      )
    }
  }

  private fun request(query: String): List<PlaceCandidate> {
    val encoded = URLEncoder.encode(query, "UTF-8")
    val url = URL("https://nominatim.openstreetmap.org/search?format=jsonv2&q=$encoded" +
      "&limit=5&accept-language=zh-CN&countrycodes=cn")
    val connection = openConnection(url)
    return try {
      connection.requestMethod = "GET"
      connection.connectTimeout = 5_000
      connection.readTimeout = 5_000
      connection.setRequestProperty("User-Agent", "WatermarkCam/0.3 (personal Android app)")
      if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("Place search HTTP ${connection.responseCode}")
      parseNominatimSearch(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
    } finally {
      connection.disconnect()
    }
  }
}
