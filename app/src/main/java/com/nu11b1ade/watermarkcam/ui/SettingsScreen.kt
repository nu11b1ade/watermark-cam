package com.nu11b1ade.watermarkcam.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nu11b1ade.watermarkcam.domain.OverrideInput
import com.nu11b1ade.watermarkcam.domain.MAX_WATERMARK_TEXT_LENGTH
import com.nu11b1ade.watermarkcam.domain.WATERMARK_TIME_FORMAT
import com.nu11b1ade.watermarkcam.domain.WatermarkConfig
import com.nu11b1ade.watermarkcam.domain.parseOverrides
import java.time.LocalDateTime

@Composable
fun SettingsScreen(
  config: WatermarkConfig,
  onSave: (WatermarkConfig) -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var showTime by rememberSaveable { mutableStateOf(config.showTime) }
  var showPlace by rememberSaveable { mutableStateOf(config.showPlace) }
  var showCoordinates by rememberSaveable { mutableStateOf(config.showCoordinates) }
  var showWeather by rememberSaveable { mutableStateOf(config.showWeather) }
  var showCustomText by rememberSaveable { mutableStateOf(config.showCustomText) }
  var customText by rememberSaveable { mutableStateOf(config.customText) }
  var timeEnabled by rememberSaveable { mutableStateOf(config.overrides.time != null) }
  var time by rememberSaveable { mutableStateOf((config.overrides.time ?: LocalDateTime.now()).format(WATERMARK_TIME_FORMAT)) }
  var placeEnabled by rememberSaveable { mutableStateOf(config.overrides.place != null) }
  var place by rememberSaveable { mutableStateOf(config.overrides.place?.address.orEmpty()) }
  var latitude by rememberSaveable { mutableStateOf(config.overrides.place?.coordinates?.latitude?.toString().orEmpty()) }
  var longitude by rememberSaveable { mutableStateOf(config.overrides.place?.coordinates?.longitude?.toString().orEmpty()) }
  var error by rememberSaveable { mutableStateOf<String?>(null) }
  BackHandler(onBack = onBack)

  Column(modifier.fillMaxSize().imePadding()) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
      TextButton(onClick = onBack) { Text("返回") }
      Text("水印设置", style = MaterialTheme.typography.titleLarge)
    }
    HorizontalDivider()
    Column(
      Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text("显示字段", style = MaterialTheme.typography.titleMedium)
      FieldSwitch("时间", showTime) { showTime = it }
      FieldSwitch("地点 / 地址", showPlace) { showPlace = it }
      FieldSwitch("纬度 / 经度", showCoordinates) { showCoordinates = it }
      FieldSwitch("天气", showWeather) { showWeather = it }
      FieldSwitch("自定义文字", showCustomText) { showCustomText = it }
      OutlinedTextField(
        value = customText,
        onValueChange = { customText = it.take(MAX_WATERMARK_TEXT_LENGTH) },
        label = { Text("自定义文字（最多 $MAX_WATERMARK_TEXT_LENGTH 字）") },
        modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 3,
      )
      HorizontalDivider(Modifier.padding(vertical = 8.dp))
      Text("手动覆盖", style = MaterialTheme.typography.titleMedium)
      Text(
        "开启后，图片水印与 EXIF 都使用手动值。隐藏字段只影响图片显示。",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
      )
      FieldSwitch("覆盖时间", timeEnabled) { timeEnabled = it }
      if (timeEnabled) {
        OutlinedTextField(
          value = time, onValueChange = { time = it.take(19) }, singleLine = true,
          label = { Text("时间（设备时区）") },
          supportingText = { Text("格式：2026-10-21 09:08:07；启用后时间固定") },
          modifier = Modifier.fillMaxWidth(),
        )
      }
      FieldSwitch("覆盖地点", placeEnabled) { placeEnabled = it }
      if (placeEnabled) {
        OutlinedTextField(
          value = place, onValueChange = { place = it.take(MAX_WATERMARK_TEXT_LENGTH) },
          label = { Text("地点 / 地址") }, modifier = Modifier.fillMaxWidth(), maxLines = 3,
        )
        OutlinedTextField(
          value = latitude, onValueChange = { latitude = it.take(24) }, singleLine = true,
          label = { Text("纬度（可留空，-90 到 90）") }, modifier = Modifier.fillMaxWidth(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
        )
        OutlinedTextField(
          value = longitude, onValueChange = { longitude = it.take(24) }, singleLine = true,
          label = { Text("经度（可留空，-180 到 180）") }, modifier = Modifier.fillMaxWidth(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
        )
        Text("坐标须同时填写或同时留空。留空时不保留真实 GPS，天气显示“天气--”。", style = MaterialTheme.typography.bodySmall)
      }
      HorizontalDivider(Modifier.padding(vertical = 8.dp))
      Text("定位不可用时仍可拍照。天气由 Open-Meteo 提供，使用有效坐标查询，短时缓存，离线显示占位。", style = MaterialTheme.typography.bodySmall)
      Text("保存后，请在微信的相册中选择照片发送。", style = MaterialTheme.typography.bodySmall)
    }
    error?.let { Text(it, Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.error) }
    Button(
      onClick = {
        parseOverrides(OverrideInput(timeEnabled, time, placeEnabled, place, latitude, longitude))
          .onSuccess { overrides ->
            onSave(config.copy(
              showTime = showTime, showPlace = showPlace, showCoordinates = showCoordinates,
              showWeather = showWeather, showCustomText = showCustomText, customText = customText,
              overrides = overrides,
            ))
          }
          .onFailure { error = it.message ?: "请检查手动覆盖值" }
      },
      modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) { Text("保存设置") }
  }
}

@Composable
private fun FieldSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Text(label, Modifier.weight(1f))
    Switch(checked = checked, onCheckedChange = onCheckedChange)
  }
}
