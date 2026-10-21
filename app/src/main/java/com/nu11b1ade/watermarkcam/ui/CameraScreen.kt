package com.nu11b1ade.watermarkcam.ui

import androidx.camera.core.ImageCapture
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nu11b1ade.watermarkcam.domain.PhotoFrame
import com.nu11b1ade.watermarkcam.domain.composeWatermark
import com.nu11b1ade.watermarkcam.location.LocationPermissionState
import com.nu11b1ade.watermarkcam.ui.theme.WarmOrange

@Composable
fun CameraScreen(
  state: CameraUiState,
  cameraGranted: Boolean,
  locationPermission: LocationPermissionState,
  onRequestCamera: () -> Unit,
  onOpenPermissions: () -> Unit,
  onLocation: () -> Unit,
  onSettings: () -> Unit,
  onCapture: (ImageCapture, PhotoFrame) -> Unit,
  onViewPhoto: () -> Unit,
  onHistory: () -> Unit = {},
  modifier: Modifier = Modifier,
  /** Replaces the CameraX preview (JVM screenshot tests only); the screen then behaves as if the camera is ready. */
  previewPlaceholder: (@Composable () -> Unit)? = null,
) {
  var capture by remember { mutableStateOf<ImageCapture?>(null) }
  var cameraError by remember { mutableStateOf<String?>(null) }
  var retry by remember { mutableIntStateOf(0) }
  var controlsHeight by remember { mutableIntStateOf(0) }
  var previewHeight by remember { mutableIntStateOf(0) }
  val bottomSpace = with(LocalDensity.current) { controlsHeight.toDp() }
  val frame = if (controlsHeight > 0 && previewHeight > controlsHeight) PhotoFrame.aboveControls(previewHeight, controlsHeight) else null
  val watermark = composeWatermark(state.config, state.observed)

  Box(modifier.fillMaxSize().background(Color.Black).onSizeChanged { previewHeight = it.height }) {
    if (cameraGranted) {
      if (previewPlaceholder != null) previewPlaceholder() else CameraPreview(
        retry = retry,
        onReady = { capture = it; if (it != null) cameraError = null },
        onError = { cameraError = it; capture = null },
        modifier = Modifier.fillMaxSize(),
      )
      if (controlsHeight > 0 && cameraError == null) {
        WatermarkOverlay(watermark.card, Modifier.fillMaxSize().padding(bottom = bottomSpace))
      }
    }
    Box(
      Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).padding(top = 64.dp, bottom = bottomSpace),
      contentAlignment = Alignment.Center,
    ) {
      if (cameraGranted && previewPlaceholder == null && capture == null && cameraError == null) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          CircularProgressIndicator(Modifier.size(32.dp), color = WarmOrange)
          Text("相机准备中…", Modifier.padding(top = 16.dp), color = Color.White)
        }
      }
      cameraError?.takeIf { cameraGranted }?.let { error ->
        CameraMessage(Modifier.padding(24.dp)) {
          Text(error, color = Color.White, textAlign = TextAlign.Center)
          Button(onClick = { cameraError = null; retry++ }) { Text("重试相机") }
        }
      }
      if (!cameraGranted) {
        CameraMessage(Modifier.padding(24.dp)) {
          Icon(Icons.Outlined.CameraAlt, contentDescription = null, tint = WarmOrange, modifier = Modifier.size(48.dp))
          Text("开启相机，记录每一天", style = MaterialTheme.typography.titleMedium, color = Color.White)
          Text("拍好的水印照片会自动保存，\n在微信相册里就能找到。", color = Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
          Button(onClick = onRequestCamera) { Text("开启相机权限") }
          TextButton(onClick = onOpenPermissions) { Text("去系统设置", color = WarmOrange) }
        }
      }
    }

    Column(
      Modifier.align(Alignment.TopCenter).fillMaxWidth()
        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent)))
        .windowInsetsPadding(WindowInsets.statusBars).padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 36.dp),
    ) {
      Row(
        Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        IconButton(
          onClick = onLocation, enabled = !state.locating,
          modifier = Modifier.size(48.dp).background(Color.White.copy(alpha = 0.12f), CircleShape)
            .semantics {
              contentDescription = if (locationPermission.isGranted) "刷新定位和天气" else "开启定位"
              if (state.locating) stateDescription = "正在刷新定位和天气"
            },
        ) {
          if (state.locating) CircularProgressIndicator(Modifier.size(22.dp), color = WarmOrange, strokeWidth = 2.dp)
          else Icon(Icons.Outlined.MyLocation, contentDescription = null, tint = Color.White)
        }
        Text("水印相机", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.9f))
        IconButton(
          onClick = onSettings, enabled = !state.saving,
          modifier = Modifier.size(48.dp).background(Color.White.copy(alpha = 0.12f), CircleShape),
        ) {
          Icon(Icons.Outlined.Settings, contentDescription = "打开设置", tint = Color.White)
        }
      }
      if (!locationPermission.isGranted && state.config.overrides.place?.coordinates == null) {
        LocationPermissionCard(
          permanentlyDenied = locationPermission == LocationPermissionState.PermanentlyDenied,
          onAuthorize = onLocation, onOpenPermissions = onOpenPermissions,
          modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
        )
      }
    }

    Column(
      Modifier.align(Alignment.BottomCenter).fillMaxWidth()
        .onSizeChanged { controlsHeight = it.height }
        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f), Color.Black.copy(alpha = 0.92f))))
        .windowInsetsPadding(WindowInsets.navigationBars).padding(top = 24.dp, bottom = 20.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text("照片", color = WarmOrange, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 16.dp))
      Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(Modifier.size(80.dp), contentAlignment = Alignment.Center) {
          GalleryThumbnail(state.lastSaved, enabled = !state.saving, onClick = onViewPhoto)
        }
        ShutterButton(
          enabled = cameraGranted && (capture != null || previewPlaceholder != null) && frame != null && !state.saving,
          saving = state.saving,
          onClick = { capture?.let { camera -> frame?.let { onCapture(camera, it) } } },
        )
        Box(Modifier.size(80.dp), contentAlignment = Alignment.Center) {
          IconButton(
            onClick = onHistory, enabled = !state.saving,
            modifier = Modifier.size(56.dp).background(Color.White.copy(alpha = 0.14f), CircleShape),
          ) {
            Icon(Icons.Outlined.History, contentDescription = "历史照片", tint = Color.White, modifier = Modifier.size(28.dp))
          }
        }
      }
      Text(
        if (state.saving) "正在保存照片…" else "拍照后自动保存到相册",
        Modifier.padding(top = 12.dp), color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall,
      )
    }
  }
}

@Composable
private fun LocationPermissionCard(
  permanentlyDenied: Boolean,
  onAuthorize: () -> Unit,
  onOpenPermissions: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(modifier, shape = RoundedCornerShape(20.dp), color = Color(0xEE24201C)) {
    Column(Modifier.padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 4.dp)) {
      Text("需要位置权限才能显示地点和天气", color = Color.White, style = MaterialTheme.typography.bodyMedium)
      Row(Modifier.align(Alignment.End)) {
        TextButton(onClick = onAuthorize) { Text("授权", color = WarmOrange) }
        if (permanentlyDenied) TextButton(onClick = onOpenPermissions) { Text("去设置", color = WarmOrange) }
      }
    }
  }
}

@Composable
private fun ShutterButton(enabled: Boolean, saving: Boolean, onClick: () -> Unit) {
  val interactions = remember { MutableInteractionSource() }
  val pressed by interactions.collectIsPressedAsState()
  val scale by animateFloatAsState(if (pressed) 0.92f else 1f, label = "快门按压")
  val alpha by animateFloatAsState(if (enabled || saving) 1f else 0.4f, label = "快门可用状态")
  Box(
    Modifier.size(80.dp).scale(scale).alpha(alpha).clip(CircleShape)
      .clickable(enabled = enabled, role = Role.Button, interactionSource = interactions, indication = null, onClick = onClick)
      .semantics { contentDescription = "拍照"; if (saving) stateDescription = "正在保存照片" }
      .padding(2.dp).border(3.dp, Color.White, CircleShape).padding(7.dp)
      .background(if (pressed) Color(0xFFDDDDDD) else Color.White, CircleShape),
    contentAlignment = Alignment.Center,
  ) {
    if (saving) CircularProgressIndicator(Modifier.size(28.dp), color = WarmOrange, strokeWidth = 3.dp)
  }
}

@Composable
private fun CameraMessage(modifier: Modifier, content: @Composable () -> Unit) {
  Surface(modifier, shape = RoundedCornerShape(28.dp), color = Color(0xEE24201C)) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
      horizontalAlignment = Alignment.CenterHorizontally) { content() }
  }
}
