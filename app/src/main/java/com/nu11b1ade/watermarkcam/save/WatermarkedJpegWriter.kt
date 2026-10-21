package com.nu11b1ade.watermarkcam.save

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import com.nu11b1ade.watermarkcam.domain.PreparedWatermark
import com.nu11b1ade.watermarkcam.domain.WatermarkContent
import com.nu11b1ade.watermarkcam.domain.photoMetadata
import com.nu11b1ade.watermarkcam.watermark.BitmapWatermarkBurner
import java.io.File
import java.io.IOException

/** Blocking image processing; callers run this on IO and own the destination's cleanup on failure. */
class WatermarkedJpegWriter {
  fun write(source: File, destination: File, watermark: PreparedWatermark) {
    val upright = readUprightBitmap(source)
    var burned: Bitmap? = null
    try {
      burned = BitmapWatermarkBurner().burn(upright, watermark.lines)
      destination.outputStream().use {
        if (!burned.compress(Bitmap.CompressFormat.JPEG, 94, it)) throw IOException("无法编码 JPEG")
      }
      // Fresh pixels cannot retain camera GPS/time hidden behind manual overrides.
      writeExif(destination, watermark.content)
    } finally {
      if (burned !== upright) burned?.recycle()
      upright.recycle()
    }
  }

  private fun readUprightBitmap(file: File): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("无法读取相机照片")
    val options = BitmapFactory.Options().apply {
      inMutable = true
      inPreferredConfig = Bitmap.Config.ARGB_8888
      inSampleSize = 1
      while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 2560) inSampleSize *= 2
    }
    val original = BitmapFactory.decodeFile(file.path, options) ?: throw IOException("无法解码相机照片")
    return try {
      val matrix = Matrix().apply {
        when (ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
          ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
          ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
          ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
          ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
          ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
          ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(270f); postScale(-1f, 1f) }
          ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
        }
      }
      Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true).also {
        if (it !== original) original.recycle()
      }
    } catch (error: Exception) {
      original.recycle()
      throw error
    }
  }

  private fun writeExif(file: File, content: WatermarkContent) {
    val metadata = photoMetadata(content)
    ExifInterface(file).apply {
      setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
      setAttribute(ExifInterface.TAG_DATETIME, metadata.dateTime)
      setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, metadata.dateTime)
      setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, metadata.dateTime)
      setAttribute(ExifInterface.TAG_OFFSET_TIME, metadata.offset)
      setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, metadata.offset)
      setAttribute(ExifInterface.TAG_OFFSET_TIME_DIGITIZED, metadata.offset)
      setAttribute(ExifInterface.TAG_USER_COMMENT, metadata.userComment)
      metadata.coordinates?.let { setLatLong(it.latitude, it.longitude) }
      setAttribute(ExifInterface.TAG_GPS_DATESTAMP, metadata.gpsDate)
      setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, metadata.gpsTime)
      saveAttributes()
    }
  }
}
