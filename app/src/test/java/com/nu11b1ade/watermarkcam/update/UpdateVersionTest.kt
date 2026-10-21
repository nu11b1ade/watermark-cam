package com.nu11b1ade.watermarkcam.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateVersionTest {
  private val manifest = UpdateManifest(12, "0.1.0", "修复问题", ApkUrls(github = "https://github.com/app.apk"), ABC_SHA256)

  @Test fun `newer compares version codes, not version names`() {
    assertTrue(isNewer(manifest, 11))
    assertFalse(isNewer(manifest, 12))
    assertFalse(isNewer(manifest, 13))
  }

  @Test fun `minimum supported version only recommends updating below its threshold`() {
    assertFalse(forceUpdate(manifest, 1))
    assertTrue(forceUpdate(manifest.copy(minSupported = 10), 9))
    assertFalse(forceUpdate(manifest.copy(minSupported = 10), 10))
    assertFalse(forceUpdate(manifest.copy(minSupported = 10), 11))
  }
}
