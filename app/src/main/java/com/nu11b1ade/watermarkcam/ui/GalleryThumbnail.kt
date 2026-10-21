package com.nu11b1ade.watermarkcam.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun GalleryThumbnail(uri: Uri?, enabled: Boolean, onClick: () -> Unit) {
  val context = LocalContext.current
  val thumbnail by produceState<ImageBitmap?>(null, uri, context) {
    value = null
    if (uri != null) value = withContext(Dispatchers.IO) { loadThumbnail(context, uri)?.asImageBitmap() }
  }
  Box(
    Modifier.size(56.dp).clip(RoundedCornerShape(14.dp))
      .background(Color.White.copy(alpha = 0.12f))
      .border(1.dp, Color.White.copy(alpha = 0.65f), RoundedCornerShape(14.dp))
      .clickable(enabled = enabled && uri != null, role = Role.Button, onClick = onClick)
      .semantics { contentDescription = if (uri == null) "相册，拍照后可查看" else "查看刚拍的照片" },
    contentAlignment = Alignment.Center,
  ) {
    val image = thumbnail
    if (image != null) Image(image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    else Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(26.dp))
  }
}

/** Decode off the main thread and cap memory even for multi-megapixel photos on Android 8–9. */
private fun loadThumbnail(context: Context, uri: Uri): Bitmap? = try {
  if (Build.VERSION.SDK_INT >= 29) {
    context.contentResolver.loadThumbnail(uri, Size(160, 160), null)
  } else {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    val options = BitmapFactory.Options().apply {
      inSampleSize = 1
      while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 160) inSampleSize *= 2
      inPreferredConfig = Bitmap.Config.RGB_565
    }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
  }
} catch (_: IOException) {
  null
} catch (_: SecurityException) {
  null
} catch (_: IllegalArgumentException) {
  null
}
