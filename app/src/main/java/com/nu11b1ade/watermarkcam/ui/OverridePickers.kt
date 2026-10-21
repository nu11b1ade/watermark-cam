package com.nu11b1ade.watermarkcam.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nu11b1ade.watermarkcam.domain.clampOverrideDate
import java.time.LocalDate
import java.time.LocalDateTime

@Composable
internal fun OverrideTimePicker(value: LocalDateTime, onDismiss: () -> Unit, onConfirm: (Int, Int) -> Unit) {
  var hour by rememberSaveable(value) { mutableIntStateOf(value.hour) }
  var minute by rememberSaveable(value) { mutableIntStateOf(value.minute) }
  var hourScrolling by remember { mutableStateOf(false) }
  var minuteScrolling by remember { mutableStateOf(false) }
  OverridePickerDialog("选择时间", onDismiss, { onConfirm(hour, minute) }, !hourScrolling && !minuteScrolling) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
      WheelPicker(0..23, hour, { hour = it }, "时", Modifier.weight(1f), onScrollingChange = { hourScrolling = it })
      WheelPicker(0..59, minute, { minute = it }, "分", Modifier.weight(1f), onScrollingChange = { minuteScrolling = it })
    }
  }
}

@Composable
internal fun OverrideDatePicker(
  value: LocalDate,
  onDismiss: () -> Unit,
  yearRange: IntRange = LocalDate.now().year.let { (it - 5)..(it + 1) },
  onConfirm: (LocalDate) -> Unit,
) {
  var epochDay by rememberSaveable(value, yearRange) {
    mutableLongStateOf(clampOverrideDate(value.year, value.monthValue, value.dayOfMonth, yearRange).toEpochDay())
  }
  val date = LocalDate.ofEpochDay(epochDay)
  fun updateDate(year: Int? = null, month: Int? = null, day: Int? = null) {
    // Read the latest draft: two wheels may settle in the same frame before recomposition.
    val current = LocalDate.ofEpochDay(epochDay)
    epochDay = clampOverrideDate(year ?: current.year, month ?: current.monthValue, day ?: current.dayOfMonth, yearRange).toEpochDay()
  }
  var yearScrolling by remember { mutableStateOf(false) }
  var monthScrolling by remember { mutableStateOf(false) }
  var dayScrolling by remember { mutableStateOf(false) }
  OverridePickerDialog("选择日期", onDismiss, { onConfirm(date) }, !yearScrolling && !monthScrolling && !dayScrolling) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      WheelPicker(yearRange, date.year, { updateDate(year = it) }, "年", Modifier.weight(1.3f), looping = false,
        formatValue = Int::toString, onScrollingChange = { yearScrolling = it })
      WheelPicker(1..12, date.monthValue, { updateDate(month = it) }, "月", Modifier.weight(1f), onScrollingChange = { monthScrolling = it })
      WheelPicker(1..date.lengthOfMonth(), date.dayOfMonth, { updateDate(day = it) }, "日", Modifier.weight(1f), onScrollingChange = { dayScrolling = it })
    }
  }
}

@Composable
private fun OverridePickerDialog(
  title: String,
  onDismiss: () -> Unit,
  onConfirm: () -> Unit,
  confirmEnabled: Boolean,
  content: @Composable () -> Unit,
) {
  Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    Surface(Modifier.widthIn(max = 400.dp).fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(28.dp),
      color = MaterialTheme.colorScheme.surfaceContainerHigh) {
      Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text("上下滑动选择", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
          TextButton(onClick = onDismiss) { Text("取消") }
          TextButton(onClick = onConfirm, enabled = confirmEnabled) { Text("确定") }
        }
      }
    }
  }
}
