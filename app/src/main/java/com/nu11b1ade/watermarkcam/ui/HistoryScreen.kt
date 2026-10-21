package com.nu11b1ade.watermarkcam.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nu11b1ade.watermarkcam.domain.WATERMARK_TIME_FORMAT
import com.nu11b1ade.watermarkcam.domain.composeWatermark
import com.nu11b1ade.watermarkcam.history.OriginalRecord
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
  records: List<OriginalRecord>,
  originalFile: (String) -> File,
  retentionDays: Int?,
  onEdit: (String) -> Unit,
  onBack: () -> Unit,
  loading: Boolean = false,
  modifier: Modifier = Modifier,
) {
  BackHandler(onBack = onBack)
  Scaffold(modifier.fillMaxSize(), topBar = { HistoryTopBar("历史照片", "返回相机", onBack) }) { padding ->
    Column(Modifier.fillMaxSize().padding(padding)) {
      Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("选择照片，重新编辑水印", style = MaterialTheme.typography.titleMedium)
        Text(retentionDays?.let { "原图保留 $it 天，可在设置中修改" } ?: "原图永久保留，可在设置中修改",
          style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      when {
        loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        records.isEmpty() -> Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
          horizontalAlignment = Alignment.CenterHorizontally) {
          Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.secondary)
          Text("还没有可重新编辑的照片（新版本之后拍的照片才能修改）", textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        else -> LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)) {
          items(records, key = { it.id }) { record ->
            val photo by rememberOriginalPhoto(originalFile(record.id), 320)
            val content = composeWatermark(record.config, record.content).content
            Card(onClick = { onEdit(record.id) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
              Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(104.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                  contentAlignment = Alignment.Center) {
                  photo.image?.let { Image(it, "历史照片缩略图 ${record.id}", Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                    ?: Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                  Text(content.capturedAt.format(WATERMARK_TIME_FORMAT), style = MaterialTheme.typography.labelLarge)
                  Text(content.place, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                  Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Edit, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("重新编辑", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryTopBar(title: String, backDescription: String, onBack: () -> Unit) {
  TopAppBar(title = { Text(title) }, navigationIcon = {
    IconButton(onBack, Modifier.size(56.dp)) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, backDescription) }
  })
}
