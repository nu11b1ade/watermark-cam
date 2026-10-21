package com.nu11b1ade.watermarkcam.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.nu11b1ade.watermarkcam.domain.MAX_WATERMARK_TEXT_LENGTH
import com.nu11b1ade.watermarkcam.domain.PreparedWatermark
import com.nu11b1ade.watermarkcam.domain.WATERMARK_TIME_FORMAT
import com.nu11b1ade.watermarkcam.domain.WatermarkConfig
import com.nu11b1ade.watermarkcam.domain.WatermarkSize
import com.nu11b1ade.watermarkcam.history.OriginalRecord
import com.nu11b1ade.watermarkcam.history.PhotoEditState
import com.nu11b1ade.watermarkcam.location.ForwardGeocoder
import com.nu11b1ade.watermarkcam.location.LocationReading
import java.io.File
import java.time.LocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoEditScreen(
  record: OriginalRecord,
  original: File,
  onSave: (PreparedWatermark) -> Unit,
  onBack: () -> Unit,
  forwardGeocoder: ForwardGeocoder,
  onUseCurrentLocation: suspend () -> LocationReading? = { null },
  saving: Boolean = false,
  initial: PhotoEditState = PhotoEditState.from(record),
  modifier: Modifier = Modifier,
) {
  var draft by rememberSaveable(record.id, stateSaver = photoEditSaver) { mutableStateOf(initial) }
  var dateOpen by rememberSaveable { mutableStateOf(false) }
  var timeOpen by rememberSaveable { mutableStateOf(false) }
  val prepared = draft.prepare(record)
  var previewWatermark by remember(record.id) { mutableStateOf(initial.prepare(record).getOrThrow()) }
  LaunchedEffect(prepared.getOrNull()) { prepared.onSuccess { previewWatermark = it } }
  val photo by rememberOriginalPhoto(original, 1280, previewWatermark)
  val selectedTime = LocalDateTime.parse(draft.time, WATERMARK_TIME_FORMAT)
  BackHandler(onBack = onBack)

  Scaffold(
    modifier.fillMaxSize().imePadding(), contentWindowInsets = WindowInsets(0, 0, 0, 0),
    topBar = { HistoryTopBar("编辑水印", "返回历史照片", onBack) },
    bottomBar = {
      Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shadowElevation = 4.dp) {
        Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 20.dp, vertical = 12.dp)) {
          prepared.exceptionOrNull()?.message?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp)) }
          Button(onClick = { prepared.onSuccess(onSave) }, enabled = !saving && prepared.isSuccess && photo.image != null && !photo.failed,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(18.dp)) {
            Text(if (saving) "正在保存…" else "保存为新照片")
          }
        }
      }
    },
  ) { padding ->
    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)) {
      Box(Modifier.fillMaxWidth().height(280.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFF1C1916)),
        contentAlignment = Alignment.Center) {
        photo.image?.let { Image(it, "照片预览", Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
          ?: Text(if (photo.failed) "无法读取原图，请返回重试" else "正在生成预览…", color = Color.White)
      }
      Text("预览与成片使用同一水印渲染 · 原照片保持不变", style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp))
      SettingsSection("拍摄时间", "水印与 EXIF 拍摄信息都会使用所选时间") {
        PickerButton("日期", draft.time.substringBefore(' '), Icons.Outlined.CalendarMonth) { dateOpen = true }
        PickerButton("时间 · 24小时制", draft.time.substringAfter(' '), Icons.Outlined.Schedule) { timeOpen = true }
      }
      SettingsSection("地点", "填写地点，自动查找经纬度或手动修改") {
        PlaceEditor(place = draft.place, latitude = draft.latitude, longitude = draft.longitude,
          onPlaceChange = { draft = draft.copy(place = it) }, onLatitudeChange = { draft = draft.copy(latitude = it) },
          onLongitudeChange = { draft = draft.copy(longitude = it) }, forwardGeocoder = forwardGeocoder,
          onUseCurrentLocation = onUseCurrentLocation, showErrors = true)
      }
      SettingsSection("天气", "可填写任意天气文字；留空隐藏此行，无坐标也可显示") {
        OutlinedTextField(draft.weatherText, { draft = draft.copy(weatherText = it.take(MAX_WATERMARK_TEXT_LENGTH)) },
          label = { Text("天气文字") }, placeholder = { Text("多云 23°C") }, singleLine = true,
          modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
      }
      SettingsSection("水印大小", "按画面比例缩放，预览与成片大小一致") {
        WatermarkSizePicker(draft.config.watermarkSize) { draft = draft.copy(config = draft.config.copy(watermarkSize = it)) }
      }
      SettingsSection("水印内容", "选择新照片上要显示的信息") {
        FieldSwitch("时间", draft.config.showTime) { draft = draft.copy(config = draft.config.copy(showTime = it)) }
        FieldSwitch("地点", draft.config.showPlace) { draft = draft.copy(config = draft.config.copy(showPlace = it)) }
        FieldSwitch("经纬度", draft.config.showCoordinates) { draft = draft.copy(config = draft.config.copy(showCoordinates = it)) }
        FieldSwitch("天气", draft.config.showWeather) { draft = draft.copy(config = draft.config.copy(showWeather = it)) }
        FieldSwitch("自定义文字", draft.config.showCustomText) { draft = draft.copy(config = draft.config.copy(showCustomText = it)) }
        OutlinedTextField(draft.config.customText, { draft = draft.copy(config = draft.config.copy(customText = it.take(MAX_WATERMARK_TEXT_LENGTH))) },
          enabled = draft.config.showCustomText, label = { Text("想写在照片上的话") }, modifier = Modifier.fillMaxWidth(), maxLines = 3,
          shape = RoundedCornerShape(14.dp))
      }
    }
  }
  if (dateOpen) OverrideDatePicker(selectedTime.toLocalDate(), { dateOpen = false }) {
    draft = draft.copy(time = selectedTime.with(it).format(WATERMARK_TIME_FORMAT))
    dateOpen = false
  }
  if (timeOpen) OverrideTimePicker(selectedTime, { timeOpen = false }) { hour, minute ->
    draft = draft.copy(time = selectedTime.withHour(hour).withMinute(minute).withSecond(0).format(WATERMARK_TIME_FORMAT))
    timeOpen = false
  }
}

private val photoEditSaver = listSaver<PhotoEditState, Any>(
  save = { listOf(it.time, it.place, it.latitude, it.longitude, it.weatherText, it.config.showTime, it.config.showPlace,
    it.config.showCoordinates, it.config.showWeather, it.config.showCustomText, it.config.customText, it.config.watermarkSize.name) },
  restore = { PhotoEditState(it[0] as String, it[1] as String, it[2] as String, it[3] as String, it[4] as String,
    WatermarkConfig(showTime = it[5] as Boolean, showPlace = it[6] as Boolean, showCoordinates = it[7] as Boolean,
      showWeather = it[8] as Boolean, showCustomText = it[9] as Boolean, customText = it[10] as String,
      watermarkSize = WatermarkSize.valueOf(it[11] as String))) },
)
