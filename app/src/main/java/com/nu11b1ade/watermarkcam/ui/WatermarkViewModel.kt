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
import com.nu11b1ade.watermarkcam.domain.PhotoFrame
import com.nu11b1ade.watermarkcam.domain.composeWatermark
import com.nu11b1ade.watermarkcam.domain.effectiveCoordinates
import com.nu11b1ade.watermarkcam.location.LocationSource
import com.nu11b1ade.watermarkcam.location.LocationReading
import com.nu11b1ade.watermarkcam.location.PlatformLocationSource
import com.nu11b1ade.watermarkcam.save.PhotoSaver
import com.nu11b1ade.watermarkcam.history.OriginalRecord
import com.nu11b1ade.watermarkcam.history.OriginalsStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
  val retentionDays: Int? = 7,
  val history: List<OriginalRecord> = emptyList(),
  val historyLoading: Boolean = false,
)

class WatermarkViewModel @JvmOverloads constructor(
  application: Application,
  private val settings: SettingsStore = SettingsStore(application),
  private val locationSource: LocationSource = PlatformLocationSource(application),
) : AndroidViewModel(application) {
  private val weatherSource = OpenMeteoWeatherSource()
  private val originals = OriginalsStore(File(application.filesDir, "originals"))
  private val photoSaver = PhotoSaver(application, originals)
  private val mutableState = MutableStateFlow(CameraUiState(
    config = settings.load(),
    retentionDays = settings.loadRetentionDays(),
    lastSaved = restoreLastSaved(),
    observed = settings.loadLastLocation().let { cached ->
      WatermarkContent(ZonedDateTime.now(), cached?.address ?: PLACE_NO_PERMISSION, cached?.coordinates)
    },
  ))
  val state = mutableState.asStateFlow()
  private var locationJob: Job? = null
  private var weatherJob: Job? = null
  private var clockJob: Job? = null
  private var foreground = false

  init { cleanupOriginals() }

  fun originalFile(id: String): File = originals.originalFile(id)

  fun updateRetention(days: Int?) {
    settings.saveRetentionDays(days)
    mutableState.update { it.copy(retentionDays = days) }
    cleanupOriginals()
  }

  private fun cleanupOriginals() {
    val days = state.value.retentionDays
    viewModelScope.launch {
      runCatching { withContext(Dispatchers.IO) { originals.cleanup(days) } }
    }
  }

  fun loadHistory() {
    mutableState.update { it.copy(historyLoading = true) }
    viewModelScope.launch {
      val records = runCatching {
        withContext(Dispatchers.IO) { originals.cleanup(state.value.retentionDays); originals.list() }
      }.getOrDefault(emptyList())
      mutableState.update { it.copy(history = records, historyLoading = false) }
    }
  }

  fun historyRecord(id: String): OriginalRecord? = state.value.history.firstOrNull { it.id == id }

  /** Renders a new gallery photo from the private original; the old photo and record stay untouched. */
  fun saveEdited(record: OriginalRecord, watermark: PreparedWatermark, onDone: () -> Unit = {}) {
    if (state.value.saving) return
    mutableState.update { it.copy(saving = true) }
    viewModelScope.launch {
      try {
        val uri = photoSaver.saveFromOriginal(originals.originalFile(record.id), watermark)
        rememberLastSaved(uri)
        mutableState.update { it.copy(lastSaved = uri, notice = Notice("已保存为新照片")) }
        onDone()
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (_: SecurityException) {
        showNotice("权限不足，请检查相册权限")
      } catch (_: Exception) {
        showNotice("保存失败，原图可能已被清理，请重试")
      } finally {
        mutableState.update { it.copy(saving = false) }
      }
    }
  }

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

  suspend fun currentLocation(): LocationReading? = locationSource.current().takeIf { it.coordinates != null }

  fun refreshEnvironment(hasLocationPermission: Boolean) {
    if (!foreground) return
    locationJob?.cancel()
    if (!hasLocationPermission) {
      mutableState.update { it.copy(
        locating = false,
        observed = it.observed.copy(place = PLACE_NO_PERMISSION, coordinates = null, weather = null),
      ) }
      return
    }
    val cached = settings.loadLastLocation()
    mutableState.update { state ->
      val coordinates = state.observed.coordinates ?: cached?.coordinates
      val place = when {
        state.observed.coordinates != null && !isPlaceholderPlace(state.observed.place) -> state.observed.place
        cached != null -> cached.address
        else -> state.observed.place
      }
      state.copy(
        locating = coordinates == null,
        observed = state.observed.copy(place = place, coordinates = coordinates),
      )
    }
    refreshWeather()
    locationJob = viewModelScope.launch {
      var reverseJob: Job? = null
      try {
        locationSource.fixes().collect { coordinates ->
          mutableState.update {
            it.copy(locating = false, observed = it.observed.copy(coordinates = coordinates))
          }
          refreshWeather()
          reverseJob?.cancel()
          reverseJob = launch {
            val resolved = try {
              locationSource.reverse(coordinates)?.takeIf { it.isNotBlank() }
            } catch (cancelled: CancellationException) {
              throw cancelled
            } catch (_: Exception) {
              null
            }
            mutableState.update { state ->
              if (state.observed.coordinates != coordinates) return@update state
              val place = resolved
                ?: state.observed.place.takeUnless(::isPlaceholderPlace)
                ?: PLACE_NO_ADDRESS
              if (resolved != null) settings.saveLastLocation(resolved, coordinates)
              state.copy(observed = state.observed.copy(place = place))
            }
          }
        }
        reverseJob?.join()
        mutableState.update { state ->
          if (state.observed.coordinates != null) state.copy(locating = false)
          else state.copy(
            locating = false,
            observed = state.observed.copy(
              place = state.observed.place.takeUnless(::isPlaceholderPlace) ?: PLACE_UNAVAILABLE,
            ),
          )
        }
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (_: Exception) {
        mutableState.update { state ->
          state.copy(
            locating = false,
            observed = if (state.observed.coordinates == null) {
              state.observed.copy(place = PLACE_UNAVAILABLE)
            } else state.observed,
          )
        }
      }
    }
  }

  private fun refreshWeather() {
    if (!foreground) return
    weatherJob?.cancel()
    val coordinates = effectiveCoordinates(state.value.config, state.value.observed)
    mutableState.update { it.copy(observed = it.observed.copy(weather = null)) }
    if (coordinates == null) return
    weatherJob = viewModelScope.launch {
      val reading = weatherSource.current(coordinates)
      mutableState.update {
        if (effectiveCoordinates(it.config, it.observed) == coordinates) {
          it.copy(observed = it.observed.copy(weather = reading))
        } else it
      }
    }
  }

  fun prepareCapture(camera: ImageCapture, frame: PhotoFrame): CaptureRequest {
    val current = state.value
    val frozen = composeWatermark(current.config, current.observed.copy(capturedAt = ZonedDateTime.now()))
    return CaptureRequest(camera, frozen.copy(frame = frame))
  }

  fun capture(request: CaptureRequest) {
    if (state.value.saving) return
    mutableState.update { it.copy(saving = true) }
    viewModelScope.launch {
      try {
        val uri = photoSaver.captureAndSave(request.camera, request.watermark)
        rememberLastSaved(uri)
        mutableState.update { it.copy(lastSaved = uri, notice = Notice("已保存到相册")) }
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

  private fun rememberLastSaved(uri: Uri) {
    settings.saveLastSavedUri(uri)
  }

  private fun restoreLastSaved(): Uri? {
    val uri = settings.loadLastSavedUri() ?: return null
    return if (uriReadable(uri)) uri else {
      settings.saveLastSavedUri(null)
      null
    }
  }

  private fun uriReadable(uri: Uri): Boolean = try {
    getApplication<Application>().contentResolver.openInputStream(uri)?.use { true } == true
  } catch (_: Exception) {
    false
  }

  private companion object {
    const val PLACE_NO_PERMISSION = "未开启定位（可手动设置）"
    const val PLACE_UNAVAILABLE = "位置暂不可用（可手动设置）"
    const val PLACE_NO_ADDRESS = "地址暂不可用（可手动设置）"

    fun isPlaceholderPlace(place: String): Boolean =
      place == PLACE_NO_PERMISSION || place == PLACE_UNAVAILABLE || place == PLACE_NO_ADDRESS
  }
}
