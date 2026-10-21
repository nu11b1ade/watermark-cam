package com.nu11b1ade.watermarkcam.ui

import android.util.Size
import android.view.Surface
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun CameraPreview(
  retry: Int,
  onReady: (ImageCapture?) -> Unit,
  onError: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val owner = LocalLifecycleOwner.current
  val currentReady = rememberUpdatedState(onReady)
  val currentError = rememberUpdatedState(onError)
  val view = remember(context) {
    PreviewView(context).apply {
      implementationMode = PreviewView.ImplementationMode.COMPATIBLE
      scaleType = PreviewView.ScaleType.FILL_CENTER
    }
  }
  AndroidView(factory = { view }, modifier = modifier)

  DisposableEffect(view, owner, retry) {
    var disposed = false
    var provider: ProcessCameraProvider? = null
    var preview: Preview? = null
    var cameraInfo: CameraInfo? = null
    val resolution = ResolutionSelector.Builder()
      .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
      .setResolutionStrategy(ResolutionStrategy(Size(1600, 1200), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
      .build()
    val capture = ImageCapture.Builder()
      .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
      .setResolutionSelector(resolution)
      .build()
    val streamObserver = Observer<PreviewView.StreamState> { stream ->
      if (!disposed) currentReady.value(if (stream == PreviewView.StreamState.STREAMING) capture else null)
    }
    val cameraObserver = Observer<CameraState> { cameraState ->
      if (!disposed && cameraState.error != null) {
        currentReady.value(null)
        currentError.value("相机暂不可用，请关闭其他相机应用后重试")
      }
    }
    currentReady.value(null)
    val future = ProcessCameraProvider.getInstance(context)
    future.addListener({
      if (!disposed) {
        try {
          val cameraProvider = future.get()
          provider = cameraProvider
          view.doOnLayout {
            if (!disposed) {
              try {
                capture.targetRotation = view.display?.rotation ?: Surface.ROTATION_0
                val livePreview = Preview.Builder().setResolutionSelector(resolution).build()
                preview = livePreview
                livePreview.setSurfaceProvider(view.surfaceProvider)
                val group = UseCaseGroup.Builder().addUseCase(livePreview).addUseCase(capture).apply {
                  view.viewPort?.let { setViewPort(it) }
                }.build()
                val camera = cameraProvider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, group)
                cameraInfo = camera.cameraInfo
                view.previewStreamState.observe(owner, streamObserver)
                camera.cameraInfo.cameraState.observe(owner, cameraObserver)
              } catch (_: Exception) {
                currentError.value("无法启动后置相机，请检查相机权限或模拟器相机设置")
              }
            }
          }
        } catch (_: Exception) {
          currentError.value("相机初始化失败，请重试")
        }
      }
    }, ContextCompat.getMainExecutor(context))

    onDispose {
      disposed = true
      view.previewStreamState.removeObserver(streamObserver)
      cameraInfo?.cameraState?.removeObserver(cameraObserver)
      preview?.let { provider?.unbind(it, capture) }
      currentReady.value(null)
    }
  }
}
