package com.nu11b1ade.watermarkcam.update

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nu11b1ade.watermarkcam.BuildConfig
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class UpdateViewModel internal constructor(
  application: Application,
  private val checker: UpdateChecker,
  private val throttle: CheckThrottle,
  private val downloader: ApkDownloader,
  private val currentVersionCode: Int,
) : AndroidViewModel(application) {
  constructor(application: Application) : this(
    application, UpdateChecker(),
    CheckThrottle(application.getSharedPreferences("updates", Context.MODE_PRIVATE)),
    ApkDownloader(application.cacheDir), BuildConfig.VERSION_CODE,
  )

  private val mutableState = MutableStateFlow(UpdateUiState())
  val state = mutableState.asStateFlow()
  private var checkJob: Job? = null
  private var downloadJob: Job? = null
  @Volatile private var downloadGeneration = 0L
  private var downloadedApk: File? = null
  private var downloadedFor: UpdateManifest? = null

  init { check() }

  fun check(manual: Boolean = false) {
    if (checkJob?.isActive == true || !throttle.isDue(manual)) return
    mutableState.update { it.copy(check = UpdateCheckState.Checking) }
    checkJob = viewModelScope.launch {
      val result = try {
        checker.check(currentVersionCode)
      } finally {
        // Failed attempts count too; writing before the request would suppress an unattempted check.
        throttle.recordAttempt()
      }
      mutableState.update { old ->
        when (result) {
          UpdateCheckResult.UpToDate -> old.copy(check = UpdateCheckState.UpToDate)
          is UpdateCheckResult.Available -> old.copy(
            check = UpdateCheckState.Available(result.manifest), dialog = dialogFor(result.manifest),
          )
          UpdateCheckResult.Failed -> old.copy(check = if (manual) UpdateCheckState.Failed else UpdateCheckState.Idle)
        }
      }
    }
  }

  fun openUpdate() {
    if (state.value.dialog != null) return
    val available = state.value.check as? UpdateCheckState.Available ?: return
    mutableState.update { it.copy(dialog = dialogFor(available.manifest)) }
  }

  fun dismissDialog() {
    cancelDownload()
    mutableState.update { it.copy(dialog = null, installRequest = null, showInstallPermission = false) }
  }

  fun downloadUpdate() {
    val manifest = state.value.dialog?.manifest ?: return
    if (downloadJob?.isActive == true || state.value.installRequest != null) return
    val cached = downloadedApk?.takeIf { downloadedFor == manifest && it.isFile }
    if (cached != null) {
      mutableState.update { it.copy(installRequest = cached, showInstallPermission = false) }
      return
    }
    val generation = ++downloadGeneration
    mutableState.update { it.copy(dialog = it.dialog?.copy(download = UpdateDownloadState.Downloading())) }
    downloadJob = viewModelScope.launch {
      try {
        val file = downloader.download(manifest) { progress ->
          mutableState.update {
            if (generation != downloadGeneration) it else it.copy(
              dialog = it.dialog?.copy(download = UpdateDownloadState.Downloading(progress)),
            )
          }
        }
        downloadedApk = file
        downloadedFor = manifest
        mutableState.update { it.copy(
          dialog = it.dialog?.copy(download = UpdateDownloadState.Idle), installRequest = file,
        ) }
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (_: Exception) {
        mutableState.update {
          if (generation != downloadGeneration) it else it.copy(dialog = it.dialog?.copy(download = UpdateDownloadState.Failed))
        }
      }
    }
  }

  fun cancelDownload() {
    ++downloadGeneration
    downloadJob?.cancel()
    downloadJob = null
    mutableState.update { old ->
      if (old.dialog?.download is UpdateDownloadState.Downloading) {
        old.copy(dialog = old.dialog.copy(download = UpdateDownloadState.Idle))
      } else old
    }
  }

  fun onInstallResult(result: InstallResult) {
    if (state.value.installRequest == null) return
    mutableState.update {
      when (result) {
        InstallResult.NeedsPermission -> it.copy(installRequest = null, showInstallPermission = true)
        InstallResult.Started -> it.copy(installRequest = null, dialog = null, showInstallPermission = false)
        InstallResult.Failed -> it.copy(installRequest = null, dialog = it.dialog?.copy(download = UpdateDownloadState.InstallFailed))
      }
    }
  }

  /** The UI calls this on return from settings; ApkInstaller checks permission again. */
  fun retryInstall() {
    mutableState.update { it.copy(showInstallPermission = false) }
    downloadUpdate()
  }

  fun dismissInstallPermission() { mutableState.update { it.copy(showInstallPermission = false) } }

  fun permissionSettingsFailed() {
    mutableState.update { it.copy(showInstallPermission = false, dialog = it.dialog?.copy(download = UpdateDownloadState.InstallFailed)) }
  }

  private fun dialogFor(manifest: UpdateManifest) = UpdateDialogState(manifest, forceUpdate(manifest, currentVersionCode))
}
