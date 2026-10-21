package com.nu11b1ade.watermarkcam.save

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.IOException

internal fun writeUprightOriginal(source: File, destination: File) {
  // Retain full-resolution clean pixels; only rendered gallery images use the existing 2560px cap.
  val upright = readUprightBitmap(source, Int.MAX_VALUE)
  try {
    destination.outputStream().use {
      if (!upright.compress(Bitmap.CompressFormat.JPEG, 94, it)) throw IOException("无法编码原图")
    }
  } finally { upright.recycle() }
}

internal fun readUprightBitmap(file: File, maxDimension: Int): Bitmap {
  require(maxDimension > 0)
  val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
  BitmapFactory.decodeFile(file.path, bounds)
  if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("无法读取照片")
  val options = BitmapFactory.Options().apply {
    inMutable = true
    inPreferredConfig = Bitmap.Config.ARGB_8888
    inSampleSize = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > maxDimension) inSampleSize *= 2
  }
  val original = BitmapFactory.decodeFile(file.path, options) ?: throw IOException("无法解码照片")
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
