package com.nu11b1ade.watermarkcam.save

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.core.content.ContextCompat
import com.nu11b1ade.watermarkcam.domain.PreparedWatermark
import com.nu11b1ade.watermarkcam.domain.WatermarkContent
import com.nu11b1ade.watermarkcam.history.OriginalRecord
import com.nu11b1ade.watermarkcam.history.OriginalsStore
import java.io.File
import java.io.IOException
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** Owns the complete capture → upright pixels → burn-in → fresh EXIF → gallery transaction. */
class PhotoSaver(
  private val context: Context,
  private val originals: OriginalsStore = OriginalsStore(File(context.filesDir, "originals")),
) {
  suspend fun captureAndSave(camera: ImageCapture, watermark: PreparedWatermark): Uri = withContext(Dispatchers.IO) {
    // Create and own the file in the same context: cancellation cannot discard an unowned File result.
    val raw = File.createTempFile("capture_", ".jpg", context.cacheDir)
    try {
      withContext(Dispatchers.Main.immediate) { capture(camera, raw) }
      saveCaptured(raw, watermark)
    } finally {
      raw.delete()
    }
  }

  /** Burn and publish an already captured JPEG. The caller retains ownership of [raw]. */
  suspend fun saveCaptured(raw: File, watermark: PreparedWatermark): Uri = withContext(Dispatchers.IO) {
    val uri = saveFromOriginal(raw, watermark)
    // The gallery transaction is already complete. Private history is best-effort.
    var upright: File? = null
    try {
      upright = File.createTempFile("original_", ".jpg", context.cacheDir)
      writeUprightOriginal(raw, upright)
      originals.save(upright, OriginalRecord(
        id = UUID.randomUUID().toString(), createdAt = System.currentTimeMillis(), galleryUri = uri.toString(),
        content = watermark.content, config = watermark.config, frame = watermark.frame,
      ))
    } catch (error: Exception) {
      Log.w("PhotoSaver", "无法保存原图，成片已保存到相册", error)
    } finally { upright?.delete() }
    uri
  }

  /** Publishes a new gallery image, leaving the original and its immutable sidecar untouched. */
  suspend fun saveFromOriginal(original: File, watermark: PreparedWatermark): Uri = withContext(Dispatchers.IO) {
    val jpeg = File.createTempFile("watermark_", ".jpg", context.cacheDir)
    try {
      ensureActive()
      WatermarkedJpegWriter().write(original, jpeg, watermark)
      ensureActive()
      saveToMediaStore(jpeg, watermark.content)
    } finally {
      jpeg.delete()
    }
  }

  private suspend fun capture(camera: ImageCapture, file: File): Unit = suspendCancellableCoroutine { continuation ->
    camera.takePicture(
      ImageCapture.OutputFileOptions.Builder(file).build(),
      ContextCompat.getMainExecutor(context),
      object : ImageCapture.OnImageSavedCallback {
        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
          if (continuation.isActive) continuation.resume(Unit) else file.delete()
        }

        override fun onError(exception: ImageCaptureException) {
          if (continuation.isActive) continuation.resumeWithException(exception) else file.delete()
        }
      },
    )
  }

  private fun saveToMediaStore(jpeg: File, content: WatermarkContent): Uri {
    val name = "WatermarkCam_${content.capturedAt.format(DateTimeFormatter.ofPattern("uuuuMMdd_HHmmss", Locale.ROOT))}" +
      "_${UUID.randomUUID().toString().take(8)}.jpg"
    val values = ContentValues().apply {
      put(MediaStore.Images.Media.DISPLAY_NAME, name)
      put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
      put(MediaStore.Images.Media.DATE_TAKEN, content.capturedAt.toInstant().toEpochMilli())
      if (Build.VERSION.SDK_INT >= 29) {
        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/WatermarkCam/")
        put(MediaStore.Images.Media.IS_PENDING, 1)
      } else {
        @Suppress("DEPRECATION")
        val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "WatermarkCam")
        if (!directory.exists() && !directory.mkdirs()) throw IOException("无法创建相册目录")
        @Suppress("DEPRECATION")
        put(MediaStore.Images.Media.DATA, File(directory, name).path)
      }
    }
    val resolver = context.contentResolver
    val collection = if (Build.VERSION.SDK_INT >= 29) {
      MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val uri = resolver.insert(collection, values) ?: throw IOException("无法创建相册记录")
    try {
      val output = resolver.openOutputStream(uri, "w") ?: throw IOException("无法写入相册")
      output.use { stream -> jpeg.inputStream().use { it.copyTo(stream) } }
      if (Build.VERSION.SDK_INT >= 29) {
        val published = resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        if (published < 1) throw IOException("无法发布到相册")
      }
      return uri
    } catch (error: Exception) {
      runCatching { resolver.delete(uri, null, null) }
      throw error
    }
  }
}
