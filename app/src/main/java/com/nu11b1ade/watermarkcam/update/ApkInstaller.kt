package com.nu11b1ade.watermarkcam.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File

sealed interface InstallResult {
  data object NeedsPermission : InstallResult
  data object Started : InstallResult
  data object Failed : InstallResult
}

class ApkInstaller(private val context: Context) {
  fun permissionIntent(): Intent = Intent(
    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri(),
  )

  /** Call again after returning from settings: permission is checked on every install attempt. */
  fun install(file: File): InstallResult {
    if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
      return InstallResult.NeedsPermission
    }
    return try {
      val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
      context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
      InstallResult.Started
    } catch (_: ActivityNotFoundException) {
      InstallResult.Failed
    } catch (_: SecurityException) {
      InstallResult.Failed
    } catch (_: IllegalArgumentException) {
      InstallResult.Failed
    }
  }
}
