package com.nu11b1ade.watermarkcam.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

const val AMAP_PRIVACY_URL = "https://lbs.amap.com/pages/privacy/"

@Composable
fun AMapPrivacyDialog(onChoice: (Boolean) -> Unit, onOpenPolicy: () -> Unit, onDismiss: () -> Unit = {}) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("高德地点服务隐私说明") },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("为提供更准确的地点候选和地址查询，水印相机接入高德搜索 SDK（高德软件有限公司）。")
        Text("启用后，高德将处理查询所需的搜索关键词、位置坐标，以及设备信息（如设备型号、系统版本、设备标识符）和网络信息，用于提供服务与保障运行安全。具体范围和处理规则请阅读高德隐私政策。")
        Text("仅在您同意后才使用高德服务。暂不使用仍可拍照、手动填写地点和坐标，地址查询将使用系统或备用服务。您可随时在「设置 → 地点服务与隐私」中更改选择。",
          style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onOpenPolicy) { Text("阅读高德隐私政策") }
      }
    },
    confirmButton = { Button(onClick = { onChoice(true) }) { Text("同意并启用") } },
    dismissButton = { TextButton(onClick = { onChoice(false) }) { Text("暂不使用高德") } },
  )
}
