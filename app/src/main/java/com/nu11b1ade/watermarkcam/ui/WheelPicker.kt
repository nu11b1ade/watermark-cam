package com.nu11b1ade.watermarkcam.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlinx.coroutines.launch

/** Five touch-sized rows; only a settled, centered item is reported to the caller. */
@Composable
fun WheelPicker(
  values: IntRange,
  value: Int,
  onValueChange: (Int) -> Unit,
  label: String,
  modifier: Modifier = Modifier,
  looping: Boolean = true,
  formatValue: (Int) -> String = { it.toString().padStart(2, '0') },
  onScrollingChange: (Boolean) -> Unit = {},
) {
  require(!values.isEmpty() && value in values)
  // In particular, February's new range must not reinterpret a 31-day virtual index modulo 28.
  key(values, looping) {
    val count = values.count()
    val itemCount = if (looping) count * 10_000 else count
    val initialIndex = (if (looping) itemCount / 2 / count * count else 0) + value - values.first
    val list = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val scope = rememberCoroutineScope()
    val currentValue by rememberUpdatedState(value)
    val reportValue by rememberUpdatedState(onValueChange)
    val reportScrolling by rememberUpdatedState(onScrollingChange)
    val centeredIndex by remember(list) {
      derivedStateOf {
        val layout = list.layoutInfo
        val center = (layout.viewportStartOffset + layout.viewportEndOffset) / 2f
        layout.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2f - center) }?.index
      }
    }
    fun valueAt(index: Int) = values.first + index % count
    fun indexFor(target: Int): Int {
      if (!looping) return target - values.first
      val near = centeredIndex ?: initialIndex
      val sameCycle = near / count * count + target - values.first
      return listOf(sameCycle - count, sameCycle, sameCycle + count)
        .filter { it in 0 until itemCount }.minBy { abs(it - near) }
    }

    LaunchedEffect(value) {
      if (valueAt(centeredIndex ?: initialIndex) != value) list.scrollToItem(indexFor(value))
    }
    LaunchedEffect(list) {
      snapshotFlow { list.isScrollInProgress to centeredIndex }.collect { (scrolling, index) ->
        reportScrolling(scrolling)
        if (!scrolling && index != null) {
          val settled = valueAt(index)
          if (settled != currentValue) reportValue(settled)
        }
      }
    }

    val rowHeight = 52.dp
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
      Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp))
      Box(Modifier.fillMaxWidth().height(rowHeight * 5).clip(RoundedCornerShape(16.dp))) {
        Box(Modifier.align(Alignment.Center).fillMaxWidth().height(rowHeight)
          .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), RoundedCornerShape(12.dp)))
        LazyColumn(
          state = list,
          flingBehavior = rememberSnapFlingBehavior(list, snapPosition = SnapPosition.Center),
          contentPadding = PaddingValues(vertical = rowHeight * 2),
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier.fillMaxWidth().height(rowHeight * 5).semantics {
            contentDescription = label
            stateDescription = "${formatValue(centeredIndex?.let(::valueAt) ?: value)}$label"
            progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), values.first.toFloat()..values.last.toFloat(), (count - 2).coerceAtLeast(0))
            setProgress { target ->
              scope.launch { list.animateScrollToItem(indexFor(target.toInt().coerceIn(values))) }
              true
            }
          },
        ) {
          items(itemCount) { index ->
            val layout = list.layoutInfo
            val center = (layout.viewportStartOffset + layout.viewportEndOffset) / 2f
            val item = layout.visibleItemsInfo.firstOrNull { it.index == index }
            val distance = (item?.let { abs(it.offset + it.size / 2f - center) / it.size } ?: 2f).coerceIn(0f, 2f)
            Box(
              Modifier.fillMaxWidth().height(rowHeight)
                .clickable { scope.launch { list.animateScrollToItem(index) } }
                .semantics { selected = index == centeredIndex },
              contentAlignment = Alignment.Center,
            ) {
              Text(formatValue(valueAt(index)), fontSize = (28f - distance * 4f).sp,
                fontWeight = if (index == centeredIndex) FontWeight.SemiBold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 1f - distance * 0.3f))
            }
          }
        }
      }
    }
  }
}
