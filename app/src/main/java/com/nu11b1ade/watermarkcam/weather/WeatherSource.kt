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
        val reading = parseOpenMeteoWeather(connection.inputStream.bufferedReader().use { it.readText() }, coordinates)
          ?: return@withContext fallback.current(coordinates)
        cache = Cache(SystemClock.elapsedRealtime(), reading)
        reading
      } catch (_: IOException) {
        fallback.current(coordinates)
      } finally {
        connection.disconnect()
      }
    }
  }
}
