package com.nu11b1ade.watermarkcam

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.withResumed
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nu11b1ade.watermarkcam.location.AndroidForwardGeocoder
import com.nu11b1ade.watermarkcam.location.FallbackForwardGeocoder
import com.nu11b1ade.watermarkcam.location.NominatimForwardGeocoder
import com.nu11b1ade.watermarkcam.ui.CameraScreen
import com.nu11b1ade.watermarkcam.ui.CaptureRequest
import com.nu11b1ade.watermarkcam.ui.HistoryScreen
import com.nu11b1ade.watermarkcam.ui.InstallPermissionDialog
import com.nu11b1ade.watermarkcam.ui.PhotoEditScreen
import com.nu11b1ade.watermarkcam.ui.SettingsScreen
import com.nu11b1ade.watermarkcam.ui.UpdateDialog
import com.nu11b1ade.watermarkcam.ui.WatermarkViewModel
import com.nu11b1ade.watermarkcam.ui.theme.WatermarkCamTheme
import com.nu11b1ade.watermarkcam.update.ApkInstaller
import com.nu11b1ade.watermarkcam.update.UpdateCheckState
import com.nu11b1ade.watermarkcam.update.UpdateViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
    setContent {
      WatermarkCamTheme {
        WatermarkApp(window)
      }
    }
  }
}

@Composable
private fun WatermarkApp(
  window: Window,
  model: WatermarkViewModel = viewModel(),
  updateModel: UpdateViewModel = viewModel(),
) {
  val context = LocalContext.current
  val owner = LocalLifecycleOwner.current
  val state by model.state.collectAsStateWithLifecycle()
  val updateState by updateModel.state.collectAsStateWithLifecycle()
  val installer = remember(context) { ApkInstaller(context) }
  val installPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
    updateModel.retryInstall()
  }
  LaunchedEffect(updateState.installRequest) {
    updateState.installRequest?.let { file ->
      owner.lifecycle.withResumed { updateModel.onInstallResult(installer.install(file)) }
    }
  }
  // "camera" | "settings" | "history" | "edit:<id>"
  var screen by rememberSaveable { mutableStateOf("camera") }
  val settingsOpen = screen != "camera"
  // Restored after process death: the in-memory history list must be reloaded.
  LaunchedEffect(Unit) { if (screen == "history" || screen.startsWith("edit:")) model.loadHistory() }
  var cameraRequested by rememberSaveable { mutableStateOf(false) }
  var cameraGranted by remember { mutableStateOf(context.hasPermission(Manifest.permission.CAMERA)) }
  var locationGranted by remember { mutableStateOf(context.hasLocationPermission()) }
  var pendingCapture by remember { mutableStateOf<CaptureRequest?>(null) }
  val snackbar = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  val darkTheme = isSystemInDarkTheme()
  val forwardGeocoder = remember(context) {
    FallbackForwardGeocoder(AndroidForwardGeocoder(context.applicationContext), NominatimForwardGeocoder())
  }

  SideEffect {
    val controller = WindowCompat.getInsetsController(window, window.decorView)
    controller.isAppearanceLightStatusBars = settingsOpen && !darkTheme
    controller.isAppearanceLightNavigationBars = settingsOpen && !darkTheme
  }

  val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
    cameraGranted = it
  }
  val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
    locationGranted = context.hasLocationPermission()
    model.refreshEnvironment(locationGranted)
  }
  val legacyStoragePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
    val capture = pendingCapture
    pendingCapture = null
    if (granted && capture != null) scope.launch {
      owner.lifecycle.withResumed { model.capture(capture) }
    }
    else if (!granted) model.showNotice("Android 8–9 需要相册写入权限才能保存，请在系统设置中开启")
  }

  LaunchedEffect(Unit) {
    if (!cameraGranted && !cameraRequested) {
      cameraRequested = true
      cameraPermission.launch(Manifest.permission.CAMERA)
    }
  }
  DisposableEffect(owner, context, model) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        cameraGranted = context.hasPermission(Manifest.permission.CAMERA)
        locationGranted = context.hasLocationPermission()
        model.enterForeground(locationGranted)
      } else if (event == Lifecycle.Event.ON_PAUSE) {
        model.leaveForeground()
      }
    }
    owner.lifecycle.addObserver(observer)
    onDispose {
      owner.lifecycle.removeObserver(observer)
      model.leaveForeground()
    }
  }
  LaunchedEffect(state.notice?.id) {
    state.notice?.let {
      snackbar.showSnackbar(it.message)
      model.dismissNotice(it.id)
    }
  }

  Box(Modifier.fillMaxSize()) {
    val editId = screen.removePrefix("edit:").takeIf { screen.startsWith("edit:") }
    val editRecord = editId?.let(model::historyRecord)
    if (screen == "settings") {
      SettingsScreen(
        config = state.config,
        onSave = { model.updateConfig(it); screen = "camera" },
        onBack = { screen = "camera" },
        forwardGeocoder = forwardGeocoder,
        onUseCurrentLocation = {
          if (!context.hasLocationPermission()) throw SecurityException("Location permission required")
          model.currentLocation()
        },
        retentionDays = state.retentionDays,
        onRetentionChange = model::updateRetention,
        updateState = updateState.check,
        onCheckUpdate = {
          if (updateState.check is UpdateCheckState.Available) updateModel.openUpdate()
          else updateModel.check(manual = true)
        },
      )
    } else if (screen == "history" || (editId != null && editRecord == null)) {
      LaunchedEffect(Unit) { if (editId != null) screen = "history" }
      HistoryScreen(
        records = state.history,
        originalFile = model::originalFile,
        retentionDays = state.retentionDays,
        loading = state.historyLoading,
        onEdit = { screen = "edit:$it" },
        onBack = { screen = "camera" },
      )
    } else if (editRecord != null) {
      PhotoEditScreen(
        record = editRecord,
        original = model.originalFile(editRecord.id),
        saving = state.saving,
        onSave = { watermark ->
          if (Build.VERSION.SDK_INT >= 29 || context.hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            model.saveEdited(editRecord, watermark) { screen = "history" }
          } else {
            model.showNotice("Android 8–9 需要相册写入权限才能保存，请先在相机界面拍一张授权")
          }
        },
        onBack = { screen = "history" },
        forwardGeocoder = forwardGeocoder,
        onUseCurrentLocation = {
          if (!context.hasLocationPermission()) throw SecurityException("Location permission required")
          model.currentLocation()
        },
      )
    } else {
      CameraScreen(
        state = state,
        cameraGranted = cameraGranted,
        locationGranted = locationGranted,
        onRequestCamera = { cameraRequested = true; cameraPermission.launch(Manifest.permission.CAMERA) },
        onOpenPermissions = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())) },
        onLocation = {
          if (locationGranted) model.refreshEnvironment(true)
          else locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        },
        onSettings = { screen = "settings" },
        onHistory = { model.loadHistory(); screen = "history" },
        onCapture = { capture, frame ->
          val request = model.prepareCapture(capture, frame)
          if (Build.VERSION.SDK_INT >= 29 || context.hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            model.capture(request)
          } else {
            pendingCapture = request
            legacyStoragePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
          }
        },
        onViewPhoto = {
          state.lastSaved?.let { uri ->
            try {
              context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "image/jpeg")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            } catch (_: ActivityNotFoundException) {
              model.showNotice("请打开系统相册，查看 WatermarkCam 相册")
            }
          }
        },
      )
    }
    SnackbarHost(
      snackbar,
      Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars)
        .padding(bottom = if (settingsOpen) 96.dp else 164.dp),
    )
  }
  if (updateState.showInstallPermission) {
    InstallPermissionDialog(
      onSettings = {
        try {
          installPermission.launch(installer.permissionIntent())
        } catch (_: ActivityNotFoundException) {
          updateModel.permissionSettingsFailed()
        } catch (_: SecurityException) {
          updateModel.permissionSettingsFailed()
        }
      },
      onDismiss = updateModel::dismissInstallPermission,
    )
  } else updateState.dialog?.let {
    UpdateDialog(it, onUpdate = updateModel::downloadUpdate, onDismiss = updateModel::dismissDialog,
      onCancelDownload = updateModel::cancelDownload)
  }
}

private fun Context.hasPermission(permission: String): Boolean =
  ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

private fun Context.hasLocationPermission(): Boolean =
  hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) || hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
