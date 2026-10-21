package com.nu11b1ade.watermarkcam.update

/** All update endpoints live here; sources are always tried in GitHub, then Gitee order. */
object UpdateConfig {
  const val GITHUB_BASE = "https://github.com/nu11b1ade/watermark-cam"
  const val GITEE_OWNER = "nullblade"
  const val GITEE_BASE = "https://gitee.com/$GITEE_OWNER/watermark-cam"
  const val MANIFEST_TIMEOUT_MS = 5_000

  /**
   * Gitee has no /releases/latest alias. Maintain a rolling release tagged `latest`, replacing
   * its latest.json and APK assets on every release, alongside the immutable version-tagged release.
   */
  val manifestUrls = listOf(
    "$GITHUB_BASE/releases/latest/download/latest.json",
    "$GITEE_BASE/releases/download/latest/latest.json",
  )
}
