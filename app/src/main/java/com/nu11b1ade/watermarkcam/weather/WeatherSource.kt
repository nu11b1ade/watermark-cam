package com.nu11b1ade.watermarkcam.weather

import android.os.SystemClock
import com.nu11b1ade.watermarkcam.domain.Coordinates
import com.nu11b1ade.watermarkcam.domain.WeatherReading
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

interface WeatherSource {
  suspend fun current(coordinates: Coordinates): WeatherReading?
}

class OfflineWeatherSource : WeatherSource {
  override suspend fun current(coordinates: Coordinates): WeatherReading? = null
}

class OpenMeteoWeatherSource(
  private val fallback: WeatherSource = OfflineWeatherSource(),
) : WeatherSource {
  private data class Cache(val savedAt: Long, val reading: WeatherReading)
  private var cache: Cache? = null
  private val mutex = Mutex()

  override suspend fun current(coordinates: Coordinates): WeatherReading? = withContext(Dispatchers.IO) {
    mutex.withLock {
      cache?.takeIf {
        SystemClock.elapsedRealtime() - it.savedAt < 10 * 60_000 &&
          abs(it.reading.coordinates.latitude - coordinates.latitude) < 0.01 &&
          abs(it.reading.coordinates.longitude - coordinates.longitude) < 0.01
      }?.let { return@withContext it.reading.copy(coordinates = coordinates) }

      val url = URL("https://api.open-meteo.com/v1/forecast?latitude=${coordinates.latitude}" +
        "&longitude=${coordinates.longitude}&current=temperature_2m,weather_code")
      val connection = url.openConnection() as HttpURLConnection
      try {
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        connection.setRequestProperty("User-Agent", "WatermarkCam/0.1 (Android)")
        if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext fallback.current(coordinates)
        val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).getJSONObject("current")
        val temperature = json.optDouble("temperature_2m", Double.NaN)
        if (!temperature.isFinite()) return@withContext fallback.current(coordinates)
        val reading = WeatherReading(coordinates, temperature, briefCondition(json.optInt("weather_code", -1)))
        cache = Cache(SystemClock.elapsedRealtime(), reading)
        reading
      } catch (_: IOException) {
        fallback.current(coordinates)
      } catch (_: JSONException) {
        fallback.current(coordinates)
      } finally {
        connection.disconnect()
      }
    }
  }

  private fun briefCondition(code: Int): String = when (code) {
    0 -> "晴"
    1 -> "大致晴"
    2 -> "多云"
    3 -> "阴"
    45, 48 -> "雾"
    51, 53, 55 -> "毛毛雨"
    56, 57 -> "冻毛毛雨"
    61, 63, 65 -> "雨"
    66, 67 -> "冻雨"
    71, 73, 75, 77 -> "雪"
    80, 81, 82 -> "阵雨"
    85, 86 -> "阵雪"
    95, 96, 99 -> "雷雨"
    else -> "未知"
  }
}
