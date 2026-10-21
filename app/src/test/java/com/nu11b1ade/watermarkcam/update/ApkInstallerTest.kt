package com.nu11b1ade.watermarkcam.update

import android.content.Intent
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ApkInstallerTest {
  private val app = RuntimeEnvironment.getApplication()

  @Test fun `missing install permission asks for settings and returning with permission installs the same APK`() {
    val apk = File(app.cacheDir, "updates/test.apk").apply { parentFile!!.mkdirs(); writeText("verified APK") }
    val installer = ApkInstaller(app)
    shadowOf(app.packageManager).setCanRequestPackageInstalls(false)

    assertEquals(InstallResult.NeedsPermission, installer.install(apk))
    assertNull(shadowOf(app).nextStartedActivity)
    assertEquals(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, installer.permissionIntent().action)
    assertEquals("package:${app.packageName}", installer.permissionIntent().dataString)

    shadowOf(app.packageManager).setCanRequestPackageInstalls(true)
    assertEquals("content", FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", apk).scheme)
    assertEquals(InstallResult.Started, installer.install(apk))
    val intent = shadowOf(app).nextStartedActivity
    assertEquals(Intent.ACTION_VIEW, intent.action)
    assertEquals("application/vnd.android.package-archive", intent.type)
    assertEquals("content", intent.data!!.scheme)
    assertEquals("${app.packageName}.fileprovider", intent.data!!.authority)
    assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)

    // Exercise the provider in this same sandbox: its AndroidX path cache survives test methods.
    val privateFile = File(app.cacheDir, "private.txt").apply { writeText("private") }
    assertThrows(IllegalArgumentException::class.java) {
      FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", privateFile)
    }
  }
}
