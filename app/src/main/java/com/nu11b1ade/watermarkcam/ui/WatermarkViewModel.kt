package com.nu11b1ade.watermarkcam.ui

import android.app.Application
import android.net.Uri
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nu11b1ade.watermarkcam.data.SettingsStore
import com.nu11b1ade.watermarkcam.domain.WatermarkConfig
import com.nu11b1ade.watermarkcam.domain.WatermarkContent
import com.nu11b1ade.watermarkcam.domain.PreparedWatermark
import com.nu11b1ade.watermarkcam.domain.composeWatermark
import com.nu11b1ade.watermarkcam.location.FixedLocationSource
import com.nu11b1ade.watermarkcam.location.PlatformLocationSource
import com.nu11b1ade.watermarkcam.save.PhotoSaver
import com.nu11b1ade.watermarkcam.weather.OpenMeteoWeatherSource
import java.io.IOException
import java.time.ZonedDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class Notice(val message: String, val id: Long = System.nanoTime())

data class CaptureRequest(val camera: ImageCapture, val watermark: PreparedWatermark)

data class CameraUiState(
  val config: WatermarkConfig,
  val observed: WatermarkContent,
  val locating: Boolean = false,
  val saving: Boolean = false,
  val lastSaved: Uri? = null,
  val notice: Notice? = null,
)

class WatermarkViewModel(application: Application) : AndroidViewModel(application) {
  private val settings = SettingsStore(application)
  private val locationSource = PlatformLocationSource(application)
  private val weatherSource = OpenMeteoWeatherSource()
  private val photoSaver = PhotoSaver(application)
  private val mutableState = MutableStateFlow(CameraUiState(
    config = settings.load(),
    observed = WatermarkContent(ZonedDateTime.now(), "未开启定位（可手动设置）"),
  ))
  val state = mutableState.asStateFlow()
  private var locationJob: Job? = null
  private var weatherJob: Job? = null
  private var clockJob: Job? = null
  private var foreground = false

  fun enterForeground(hasLocationPermission: Boolean) {
    foreground = true
    if (clockJob?.isActive != true) {
      clockJob = viewModelScope.launch {
        while (isActive) {
          mutableState.update { it.copy(observed = it.observed.copy(capturedAt = ZonedDateTime.now())) }
          delay(1_000)
        }
      }
    }
    refreshEnvironment(hasLocationPermission)
  }

  fun leaveForeground() {
    foreground = false
    clockJob?.cancel()
    locationJob?.cancel()
    weatherJob?.cancel()
    mutableState.update { it.copy(locating = false) }
  }

  fun updateConfig(config: WatermarkConfig) {
    settings.save(config)
    mutableState.update { it.copy(config = config, notice = Notice("设置已保存")) }
    refreshWeather()
  }

  fun refreshEnvironment(hasLocationPermission: Boolean) {
    if (!foreground) return
    locationJob?.cancel()
    if (!hasLocationPermission) {
      mutableState.update { it.copy(
        locating = false,
        observed = it.observed.copy(place = "未开启定位（可手动设置）", coordinates = null, weather = null),
      ) }
      refreshWeather()
      return
    }
    refreshWeather()
    locationJob = viewModelScope.launch {
      mutableState.update { it.copy(locating = true) }
      val location = try {
        locationSource.current()
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (_: Exception) {
        FixedLocationSource().current()
      }
      mutableState.update { it.copy(
        locating = false,
        observed = it.observed.copy(place = location.address, coordinates = location.coordinates),
      ) }
      refreshWeather()
    }
  }

  private fun refreshWeather() {
    if (!foreground) return
    weatherJob?.cancel()
    val coordinates = composeWatermark(state.value.config, state.value.observed).content.coordinates
    mutableState.update { it.copy(observed = it.observed.copy(weather = null)) }
    if (coordinates == null) return
    weatherJob = viewModelScope.launch {
      val reading = weatherSource.current(coordinates)
      mutableState.update {
        if (composeWatermark(it.config, it.observed).content.coordinates == coordinates) {
          it.copy(observed = it.observed.copy(weather = reading))
        } else it
      }
    }
  }

  fun prepareCapture(camera: ImageCapture): CaptureRequest {
    val current = state.value
    val frozen = composeWatermark(current.config, current.observed.copy(capturedAt = ZonedDateTime.now()))
    return CaptureRequest(camera, frozen)
  }

  fun capture(request: CaptureRequest) {
    if (state.value.saving) return
    mutableState.update { it.copy(saving = true) }
    viewModelScope.launch {
      try {
        val uri = photoSaver.captureAndSave(request.camera, request.watermark)
        mutableState.update { it.copy(lastSaved = uri, notice = Notice("已保存到相册 · Pictures/WatermarkCam")) }
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (_: ImageCaptureException) {
        showNotice("拍照失败，请稍后重试")
      } catch (_: SecurityException) {
        showNotice("权限不足，请检查相机与相册权限")
      } catch (_: IOException) {
        showNotice("保存失败，请检查可用空间后重试")
      } catch (_: Exception) {
        showNotice("拍照未完成，请重试")
      } finally {
        mutableState.update { it.copy(saving = false) }
      }
    }
  }

  fun showNotice(message: String) {
    mutableState.update { it.copy(notice = Notice(message)) }
  }

  fun dismissNotice(id: Long) {
    mutableState.update { if (it.notice?.id == id) it.copy(notice = null) else it }
  }
}
