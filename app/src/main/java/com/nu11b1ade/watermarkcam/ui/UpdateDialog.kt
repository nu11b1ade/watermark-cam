package com.nu11b1ade.watermarkcam.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nu11b1ade.watermarkcam.update.UpdateDialogState
import com.nu11b1ade.watermarkcam.update.UpdateDownloadState
import kotlin.math.roundToInt

@Composable
fun UpdateDialog(
  state: UpdateDialogState,
  onUpdate: () -> Unit,
  onDismiss: () -> Unit,
  onCancelDownload: () -> Unit,
) {
  val downloading = state.download as? UpdateDownloadState.Downloading
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("发现新版本 v${state.manifest.versionName}") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (state.recommendImmediate) {
          Text("当前版本过旧，建议立即更新", color = MaterialTheme.colorScheme.error)
        }
        Text(state.manifest.notes, Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()))
        when (val download = state.download) {
          is UpdateDownloadState.Downloading -> {
            val progress = download.progress
            if (progress == null) {
              LinearProgressIndicator(Modifier.fillMaxWidth())
              Text("正在下载…")
            } else {
              LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
              Text("${(progress * 100).roundToInt()}%")
            }
          }
          UpdateDownloadState.Failed -> Text("下载失败，请稍后再试", color = MaterialTheme.colorScheme.error)
          UpdateDownloadState.InstallFailed -> Text("无法打开安装程序或设置，请稍后再试", color = MaterialTheme.colorScheme.error)
          UpdateDownloadState.Idle -> Unit
        }
      }
    },
    confirmButton = {
      if (downloading != null) TextButton(onClick = onCancelDownload) { Text("取消") }
      else Button(onClick = onUpdate) { Text("更新") }
    },
    dismissButton = {
      if (downloading == null) TextButton(onClick = onDismiss) { Text("以后再说") }
    },
  )
}

@Composable
fun InstallPermissionDialog(onSettings: () -> Unit, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("允许安装新版本") },
    text = { Text("为了安装新版本，需要允许「水印相机」安装应用。点「去设置」后打开「允许来自此来源的应用」开关，再返回点更新。") },
    confirmButton = { TextButton(onClick = onSettings) { Text("去设置") } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
  )
}
