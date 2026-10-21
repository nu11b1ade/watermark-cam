package com.nu11b1ade.watermarkcam.ui

import androidx.camera.core.ImageCapture
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nu11b1ade.watermarkcam.domain.composeWatermark
import com.nu11b1ade.watermarkcam.watermark.WatermarkStyle

@Composable
fun CameraScreen(
  state: CameraUiState,
  cameraGranted: Boolean,
  locationGranted: Boolean,
  onRequestCamera: () -> Unit,
  onOpenPermissions: () -> Unit,
  onLocation: () -> Unit,
  onSettings: () -> Unit,
  onCapture: (ImageCapture) -> Unit,
  onViewPhoto: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var capture by remember { mutableStateOf<ImageCapture?>(null) }
  var cameraError by remember { mutableStateOf<String?>(null) }
  var retry by remember { mutableIntStateOf(0) }
  val watermark = composeWatermark(state.config, state.observed)
  val overridden = state.config.overrides.time != null || state.config.overrides.place != null

  Column(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
      Text("水印相机", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
      TextButton(onClick = onSettings, enabled = !state.saving) { Text("设置") }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
      Text(
        if (overridden) "手动覆盖已开启 · 图片与 EXIF 同步" else "记录日常，保存后在微信中发送",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.weight(1f),
      )
      TextButton(onClick = onLocation, enabled = !state.locating) {
        Text(if (state.locating) "定位中…" else if (locationGranted) "刷新定位" else "启用定位")
      }
    }
    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().background(Color.Black), contentAlignment = Alignment.Center) {
      if (cameraGranted) {
        val width = minOf(maxWidth, maxHeight * 0.75f)
        Box(Modifier.width(width).aspectRatio(0.75f).clip(RoundedCornerShape(8.dp))) {
          CameraPreview(
            retry = retry,
            onReady = { capture = it; if (it != null) cameraError = null },
            onError = { cameraError = it; capture = null },
            modifier = Modifier.fillMaxSize(),
          )
          WatermarkOverlay(watermark.lines, Modifier.align(Alignment.BottomStart))
          if (capture == null && cameraError == null) {
            Text("相机准备中…", Modifier.align(Alignment.TopCenter).padding(16.dp), color = Color.White)
          }
          cameraError?.let { error ->
            Column(
              Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.8f)).padding(20.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Text(error)
              OutlinedButton(onClick = { cameraError = null; retry++ }) { Text("重试相机") }
            }
          }
        }
      } else {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
          Text("开启相机权限，即可拍摄水印照片", style = MaterialTheme.typography.titleMedium)
          Text("定位可选，也可在设置中手动填写地点", Modifier.padding(vertical = 12.dp))
          Button(onClick = onRequestCamera) { Text("开启相机") }
          TextButton(onClick = onOpenPermissions) { Text("去系统设置") }
        }
      }
    }
    Row(
      Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 24.dp),
      horizontalArrangement = Arrangement.SpaceEvenly,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      TextButton(onClick = onViewPhoto, enabled = state.lastSaved != null && !state.saving) { Text("查看照片") }
      Button(
        onClick = { capture?.let(onCapture) },
        enabled = cameraGranted && capture != null && !state.saving,
        modifier = Modifier.size(80.dp),
        shape = CircleShape,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF2F5F3)),
      ) {
        if (state.saving) CircularProgressIndicator(Modifier.size(28.dp))
        else Text("拍照", color = Color(0xFF16251C), style = MaterialTheme.typography.titleMedium)
      }
      Text("自动保存\n系统相册", style = MaterialTheme.typography.bodySmall)
    }
    Text(
      if (state.saving) "正在烧录水印并保存…" else "照片保存在 Pictures/WatermarkCam",
      Modifier.padding(bottom = 12.dp), style = MaterialTheme.typography.bodySmall,
    )
  }
}

@Composable
private fun WatermarkOverlay(lines: List<String>, modifier: Modifier = Modifier) {
  if (lines.isEmpty()) return
  BoxWithConstraints(modifier.fillMaxWidth()) {
    val scale = maxWidth.value / WatermarkStyle.BASE_WIDTH
    val fontScale = LocalDensity.current.fontScale
    Text(
      text = lines.joinToString("\n"),
      color = Color.White,
      fontSize = (WatermarkStyle.FONT_SIZE * scale / fontScale).sp,
      lineHeight = (WatermarkStyle.LINE_HEIGHT * scale / fontScale).sp,
      fontFamily = FontFamily.SansSerif,
      letterSpacing = 0.sp,
      style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
      maxLines = WatermarkStyle.MAX_LINES,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.fillMaxWidth()
        .padding((WatermarkStyle.MARGIN * scale).dp)
        .clip(RoundedCornerShape((WatermarkStyle.CORNER_RADIUS * scale).dp))
        .background(Color.Black.copy(alpha = 0.65f))
        .padding((WatermarkStyle.PADDING * scale).dp),
    )
  }
}
