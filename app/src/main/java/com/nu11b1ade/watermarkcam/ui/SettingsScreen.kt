package com.nu11b1ade.watermarkcam.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nu11b1ade.watermarkcam.domain.MAX_WATERMARK_TEXT_LENGTH
import com.nu11b1ade.watermarkcam.domain.OverrideInput
import com.nu11b1ade.watermarkcam.domain.WATERMARK_TIME_FORMAT
import com.nu11b1ade.watermarkcam.domain.WatermarkConfig
import com.nu11b1ade.watermarkcam.domain.WatermarkSize
import com.nu11b1ade.watermarkcam.domain.parseOverrides
import com.nu11b1ade.watermarkcam.domain.toggleCoordinateSign
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
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
  var watermarkSize by rememberSaveable { mutableStateOf(config.watermarkSize) }
  var timeEnabled by rememberSaveable { mutableStateOf(config.overrides.time != null) }
  var time by rememberSaveable { mutableStateOf((config.overrides.time ?: LocalDateTime.now()).format(WATERMARK_TIME_FORMAT)) }
  var placeEnabled by rememberSaveable { mutableStateOf(config.overrides.place != null) }
  var place by rememberSaveable { mutableStateOf(config.overrides.place?.address.orEmpty()) }
  var latitude by rememberSaveable { mutableStateOf(config.overrides.place?.coordinates?.latitude?.toString().orEmpty()) }
  var longitude by rememberSaveable { mutableStateOf(config.overrides.place?.coordinates?.longitude?.toString().orEmpty()) }
  var saveAttempted by rememberSaveable { mutableStateOf(false) }
  var datePickerOpen by rememberSaveable { mutableStateOf(false) }
  var timePickerOpen by rememberSaveable { mutableStateOf(false) }
  val selectedTime = LocalDateTime.parse(time, WATERMARK_TIME_FORMAT)
  val overrides = parseOverrides(OverrideInput(timeEnabled, time, placeEnabled, place, latitude, longitude))
  val placeError = parseOverrides(OverrideInput(placeEnabled = placeEnabled, place = place)).exceptionOrNull()?.message
  val coordinatesError = if (placeError == null) {
    parseOverrides(OverrideInput(placeEnabled = placeEnabled, place = place, latitude = latitude, longitude = longitude))
      .exceptionOrNull()?.message
  } else null
  BackHandler(onBack = onBack)

  Scaffold(
    modifier = modifier.fillMaxSize().imePadding(),
    containerColor = MaterialTheme.colorScheme.background,
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    topBar = {
      TopAppBar(
        title = { Text("设置") },
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.size(56.dp)) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回相机")
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
      )
    },
    bottomBar = {
      Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shadowElevation = 4.dp) {
        Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 20.dp, vertical = 12.dp)) {
          if (saveAttempted && overrides.isFailure) {
            Text(overrides.exceptionOrNull()?.message.orEmpty(), Modifier.padding(bottom = 8.dp),
              color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
          }
          Button(
            onClick = {
              saveAttempted = true
              overrides.onSuccess { values ->
                onSave(config.copy(
                  showTime = showTime, showPlace = showPlace, showCoordinates = showCoordinates,
                  showWeather = showWeather, showCustomText = showCustomText, customText = customText,
                  overrides = values, watermarkSize = watermarkSize,
                ))
              }
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(18.dp),
          ) { Text("保存设置") }
        }
      }
    },
  ) { padding ->
    Column(
      Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 20.dp),
      verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
      SettingsSection("水印大小", "按画面比例缩放，预览与成片大小一致") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          WatermarkSize.entries.forEach { size ->
            FilterChip(
              selected = watermarkSize == size, onClick = { watermarkSize = size }, modifier = Modifier.weight(1f),
              label = { Text(when (size) {
                WatermarkSize.SMALL -> "小"
                WatermarkSize.MEDIUM -> "中"
                WatermarkSize.LARGE -> "大"
                WatermarkSize.XLARGE -> "特大"
              }) },
            )
          }
        }
      }
      SettingsSection("水印内容", "选择照片上要显示的信息") {
        FieldSwitch("时间", showTime) { showTime = it }
        FieldSwitch("地点", showPlace) { showPlace = it }
        FieldSwitch("经纬度", showCoordinates) { showCoordinates = it }
        FieldSwitch("天气", showWeather) { showWeather = it }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        FieldSwitch("自定义文字", showCustomText) { showCustomText = it }
        OutlinedTextField(
          value = customText, onValueChange = { customText = it.take(MAX_WATERMARK_TEXT_LENGTH) },
          enabled = showCustomText, label = { Text("想写在照片上的话") },
          supportingText = { Text("例如：平安喜乐 · ${customText.length}/$MAX_WATERMARK_TEXT_LENGTH") },
          modifier = Modifier.fillMaxWidth(), maxLines = 3, shape = RoundedCornerShape(14.dp),
        )
      }
      SettingsSection("手动时间", "照片水印与拍摄信息都会使用所选时间") {
        FieldSwitch("使用自定义时间", timeEnabled) { timeEnabled = it }
        if (timeEnabled) {
          PickerButton("日期", time.substringBefore(' '), Icons.Outlined.CalendarMonth) { datePickerOpen = true }
          PickerButton("时间 · 24小时制", time.substringAfter(' '), Icons.Outlined.Schedule) { timePickerOpen = true }
          Text("按设备时区固定时间，不随时钟变化。", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
      SettingsSection("手动地点", "定位不方便时，可以在这里填写") {
        FieldSwitch("使用自定义地点", placeEnabled) { placeEnabled = it }
        if (placeEnabled) {
          OutlinedTextField(
            value = place, onValueChange = { place = it.take(MAX_WATERMARK_TEXT_LENGTH) },
            label = { Text("地点 / 地址") }, placeholder = { Text("例如：人民公园南门") },
            isError = saveAttempted && placeError != null,
            supportingText = if (saveAttempted && placeError != null) ({ Text(placeError) }) else null,
            modifier = Modifier.fillMaxWidth(), maxLines = 3, shape = RoundedCornerShape(14.dp),
          )
          CoordinateField(
            label = "纬度", range = "范围 -90 ～ 90", value = latitude, onValueChange = { latitude = it },
            error = coordinatesError, imeAction = ImeAction.Next,
          )
          CoordinateField(
            label = "经度", range = "范围 -180 ～ 180", value = longitude, onValueChange = { longitude = it },
            error = coordinatesError, imeAction = ImeAction.Done,
          )
          Text("坐标需同时填写或同时留空。留空时，照片不会保留自动定位的坐标，天气显示暂无。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
      Text("拍好后，打开微信，从相册选择照片发送。", Modifier.padding(horizontal = 8.dp),
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }

  if (datePickerOpen) {
    OverrideDatePicker(selectedTime.toLocalDate(), onDismiss = { datePickerOpen = false }) { date ->
      time = selectedTime.with(date).format(WATERMARK_TIME_FORMAT)
      datePickerOpen = false
    }
  }
  if (timePickerOpen) {
    OverrideTimePicker(selectedTime, onDismiss = { timePickerOpen = false }) { hour, minute ->
      time = selectedTime.withHour(hour).withMinute(minute).withSecond(0).format(WATERMARK_TIME_FORMAT)
      timePickerOpen = false
    }
  }
}

@Composable
private fun SettingsSection(title: String, description: String, content: @Composable ColumnScope.() -> Unit) {
  Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(title, style = MaterialTheme.typography.titleMedium)
      Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      content()
    }
  }
}

@Composable
private fun FieldSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
  Row(
    Modifier.fillMaxWidth().heightIn(min = 60.dp)
      .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange).padding(vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
    Switch(checked = checked, onCheckedChange = null)
  }
}

@Composable
private fun PickerButton(label: String, value: String, icon: ImageVector, onClick: () -> Unit) {
  OutlinedButton(onClick, Modifier.fillMaxWidth().heightIn(min = 72.dp), shape = RoundedCornerShape(16.dp)) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.secondary)
    Column(Modifier.weight(1f).padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
  }
}

@Composable
private fun CoordinateField(
  label: String,
  range: String,
  value: String,
  onValueChange: (String) -> Unit,
  error: String?,
  imeAction: ImeAction,
) {
  OutlinedTextField(
    value = value, onValueChange = { onValueChange(it.take(24)) }, singleLine = true,
    label = { Text("$label（可留空）") }, supportingText = { Text(error ?: "$range · ± 可切换正负号") },
    isError = error != null, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
    leadingIcon = {
      IconButton(onClick = { onValueChange(toggleCoordinateSign(value)) },
        modifier = Modifier.size(48.dp).semantics { contentDescription = "切换${label}正负号" }) {
        Text("±", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
      }
    },
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = imeAction),
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OverrideDatePicker(value: LocalDate, onDismiss: () -> Unit, onConfirm: (LocalDate) -> Unit) {
  // Material3 date selections are UTC midnights, not device-local instants.
  val picker = rememberDatePickerState(
    initialSelectedDateMillis = value.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), yearRange = 1..9999,
  )
  DatePickerDialog(
    onDismissRequest = onDismiss,
    confirmButton = {
      TextButton(enabled = picker.selectedDateMillis != null, onClick = {
        picker.selectedDateMillis?.let { onConfirm(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
      }) { Text("确定") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
  ) {
    DatePicker(picker, title = { Text("选择日期", Modifier.padding(start = 24.dp, top = 20.dp, bottom = 12.dp)) })
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OverrideTimePicker(value: LocalDateTime, onDismiss: () -> Unit, onConfirm: (Int, Int) -> Unit) {
  val picker = rememberTimePickerState(initialHour = value.hour, initialMinute = value.minute, is24Hour = true)
  Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    Surface(Modifier.widthIn(max = 400.dp).fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(28.dp),
      color = MaterialTheme.colorScheme.surfaceContainerHigh) {
      Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("选择时间", style = MaterialTheme.typography.titleMedium)
        TimePicker(picker, modifier = Modifier.align(Alignment.CenterHorizontally))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
          TextButton(onClick = onDismiss) { Text("取消") }
          Spacer(Modifier.size(8.dp))
          TextButton(onClick = { onConfirm(picker.hour, picker.minute) }) { Text("确定") }
        }
      }
    }
  }
}
