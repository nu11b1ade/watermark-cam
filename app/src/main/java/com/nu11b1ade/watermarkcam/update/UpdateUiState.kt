package com.nu11b1ade.watermarkcam.update

import java.io.File

sealed interface UpdateCheckState {
  data object Idle : UpdateCheckState
  data object Checking : UpdateCheckState
  data object UpToDate : UpdateCheckState
  data class Available(val manifest: UpdateManifest) : UpdateCheckState
  data object Failed : UpdateCheckState
}

sealed interface UpdateDownloadState {
  data object Idle : UpdateDownloadState
  data class Downloading(val progress: Float? = null) : UpdateDownloadState
  data object Failed : UpdateDownloadState
  data object InstallFailed : UpdateDownloadState
}

data class UpdateDialogState(
  val manifest: UpdateManifest,
  val recommendImmediate: Boolean = false,
  val download: UpdateDownloadState = UpdateDownloadState.Idle,
)

data class UpdateUiState(
  val check: UpdateCheckState = UpdateCheckState.Idle,
  val dialog: UpdateDialogState? = null,
  val installRequest: File? = null,
  val showInstallPermission: Boolean = false,
)
