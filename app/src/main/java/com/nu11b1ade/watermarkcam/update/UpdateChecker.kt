package com.nu11b1ade.watermarkcam.update

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

sealed interface UpdateCheckResult {
  data object UpToDate : UpdateCheckResult
  data class Available(val manifest: UpdateManifest) : UpdateCheckResult
  data object Failed : UpdateCheckResult
}

class UpdateChecker(private val fetcher: (String) -> String = ::fetchManifest) {
  suspend fun check(currentVersionCode: Int): UpdateCheckResult = withContext(Dispatchers.IO) {
    for (url in UpdateConfig.manifestUrls) {
      ensureActive()
      try {
        val manifest = UpdateManifest.parse(fetcher(url)) ?: continue
        return@withContext if (isNewer(manifest, currentVersionCode)) {
          UpdateCheckResult.Available(manifest)
        } else UpdateCheckResult.UpToDate
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (_: Exception) {
        // A failed or invalid primary source must not prevent trying the mirror.
      }
    }
    UpdateCheckResult.Failed
  }
}

private fun fetchManifest(url: String): String {
  val connection = openUpdateConnection(url, UpdateConfig.MANIFEST_TIMEOUT_MS)
  return try {
    connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
  } finally {
    connection.disconnect()
  }
}
