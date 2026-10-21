package com.nu11b1ade.watermarkcam.update

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Follow release-asset redirects, but never downgrade either manifests or APKs to HTTP. */
internal fun openUpdateConnection(url: String, timeoutMs: Int): HttpURLConnection {
  var next = url
  repeat(6) {
    require(isHttpsUrl(next)) { "Only HTTPS update sources are supported" }
    val connection = (URL(next).openConnection() as HttpURLConnection).apply {
      connectTimeout = timeoutMs
      readTimeout = timeoutMs
      instanceFollowRedirects = false
      setRequestProperty("User-Agent", "WatermarkCam")
    }
    try {
      when (connection.responseCode) {
        in 200..299 -> return connection
        301, 302, 303, 307, 308 -> {
          val location = connection.getHeaderField("Location") ?: throw IOException("Missing redirect location")
          next = URL(URL(next), location).toExternalForm()
        }
        else -> throw IOException("HTTP ${connection.responseCode}")
      }
    } catch (failure: Exception) {
      connection.disconnect()
      throw failure
    }
    connection.disconnect()
  }
  throw IOException("Too many update redirects")
}
