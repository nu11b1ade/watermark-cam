package com.nu11b1ade.watermarkcam.update

import java.net.URI
import java.util.Locale
import org.json.JSONException
import org.json.JSONObject

data class ApkUrls(val github: String? = null, val gitee: String? = null) {
  val inFetchOrder: List<String> get() = listOfNotNull(github, gitee)
}

data class UpdateManifest(
  val versionCode: Int,
  val versionName: String,
  val notes: String,
  val apkUrls: ApkUrls,
  val sha256: String,
  val minSupported: Int? = null,
) {
  companion object {
    fun parse(json: String): UpdateManifest? = try {
      val value = JSONObject(json)
      val urls = value.getJSONObject("apkUrls")
      val apkUrls = ApkUrls(urls.apkUrl("github"), urls.apkUrl("gitee"))
      require(apkUrls.inFetchOrder.isNotEmpty())
      val sha256 = value.requiredString("sha256")
      require(sha256.matches(Regex("[0-9a-fA-F]{64}")))
      UpdateManifest(
        versionCode = value.positiveInt("versionCode"),
        versionName = value.requiredString("versionName").also { require(it.isNotBlank()) },
        notes = value.requiredString("notes"),
        apkUrls = apkUrls,
        sha256 = sha256.lowercase(Locale.ROOT),
        minSupported = if (value.isNull("minSupported")) null else value.positiveInt("minSupported"),
      )
    } catch (_: JSONException) {
      null
    } catch (_: IllegalArgumentException) {
      null
    }
  }
}

fun isNewer(manifest: UpdateManifest, currentVersionCode: Int): Boolean = manifest.versionCode > currentVersionCode

/** Advisory only: the update dialog always allows postponing. */
fun forceUpdate(manifest: UpdateManifest, currentVersionCode: Int): Boolean =
  manifest.minSupported?.let { currentVersionCode < it } ?: false

private fun JSONObject.requiredString(key: String): String =
  get(key) as? String ?: throw IllegalArgumentException("$key must be a string")

private fun JSONObject.positiveInt(key: String): Int {
  val value = get(key)
  require(value is Int || value is Long)
  val number = (value as Number).toLong()
  require(number in 1..Int.MAX_VALUE.toLong())
  return number.toInt()
}

private fun JSONObject.apkUrl(key: String): String? =
  if (isNull(key)) null else requiredString(key).also { require(isHttpsUrl(it)) }

internal fun isHttpsUrl(url: String): Boolean {
  val uri = URI.create(url)
  return uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
}
