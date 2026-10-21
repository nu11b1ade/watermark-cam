package com.nu11b1ade.watermarkcam.location

import com.nu11b1ade.watermarkcam.domain.Coordinates
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NominatimReverseGeocoder(
  private val openConnection: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
) : ReverseGeocoder {
  private data class CacheKey(val latitude: Int, val longitude: Int)

  private companion object {
    val cache = object : LinkedHashMap<CacheKey, String>(128, 0.75f, true) {
      override fun removeEldestEntry(eldest: MutableMap.MutableEntry<CacheKey, String>): Boolean = size > 128
    }
  }

  override suspend fun reverse(coordinates: Coordinates): String? = withContext(Dispatchers.IO) {
    val key = CacheKey((coordinates.latitude * 10_000).roundToInt(), (coordinates.longitude * 10_000).roundToInt())
    NominatimThrottle.run(
      cached = { cache[key] },
      request = { request(coordinates)?.also { cache[key] = it } },
    )
  }

  private fun request(coordinates: Coordinates): String? {
    return try {
      val url = URL("https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=${coordinates.latitude}" +
        "&lon=${coordinates.longitude}&zoom=18&accept-language=zh-CN")
      val connection = openConnection(url)
      try {
        connection.requestMethod = "GET"
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        connection.setRequestProperty("User-Agent", "WatermarkCam/0.2 (personal Android app)")
        if (connection.responseCode != HttpURLConnection.HTTP_OK) null else {
          parseNominatimAddress(connection.inputStream.bufferedReader().use { it.readText() })
        }
      } finally {
        connection.disconnect()
      }
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (_: Exception) {
      null
    }
  }
}
