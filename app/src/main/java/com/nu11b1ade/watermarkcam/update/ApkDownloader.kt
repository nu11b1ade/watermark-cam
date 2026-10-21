package com.nu11b1ade.watermarkcam.update

import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class ApkResponse(
  val input: InputStream,
  val contentLength: Long = -1,
  private val disconnect: () -> Unit = {},
) : Closeable {
  override fun close() {
    try { input.close() } finally { disconnect() }
  }
}

class ApkDownloader(
  private val cacheDir: File,
  private val fetcher: (String) -> ApkResponse = ::fetchApk,
) {
  /** Progress is null for an unknown content length; only verified APKs reach the final path. */
  suspend fun download(manifest: UpdateManifest, onProgress: (Float?) -> Unit = {}): File = withContext(Dispatchers.IO) {
    val directory = File(cacheDir, "updates")
    if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create updates cache")
    val safeVersion = manifest.versionName.replace(Regex("[^A-Za-z0-9._+-]"), "_")
    val destination = File(directory, "watermark-cam-$safeVersion.apk")
    var lastFailure: Exception? = null
    for (url in manifest.apkUrls.inFetchOrder) {
      ensureActive()
      val temporary = File.createTempFile("download-", ".part", directory)
      try {
        fetcher(url).use { response ->
          onProgress(if (response.contentLength > 0) 0f else null)
          temporary.outputStream().use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var downloaded = 0L
            while (true) {
              ensureActive()
              val count = response.input.read(buffer)
              if (count == -1) break
              ensureActive()
              output.write(buffer, 0, count)
              downloaded += count
              onProgress(if (response.contentLength > 0) {
                (downloaded.toDouble() / response.contentLength).toFloat().coerceIn(0f, 1f)
              } else null)
            }
          }
        }
        if (!Sha256.verify(temporary, manifest.sha256)) throw IOException("SHA-256 mismatch")
        ensureActive()
        if (!temporary.renameTo(destination)) throw IOException("Cannot publish verified APK")
        return@withContext destination
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (failure: Exception) {
        lastFailure = failure
      } finally {
        temporary.delete()
      }
    }
    throw IOException("All APK sources failed", lastFailure)
  }
}

private fun fetchApk(url: String): ApkResponse {
  val connection = openUpdateConnection(url, 15_000)
  return try {
    ApkResponse(connection.inputStream, connection.contentLengthLong) { connection.disconnect() }
  } catch (failure: Exception) {
    connection.disconnect()
    throw failure
  }
}
