package com.nu11b1ade.watermarkcam.save

import android.graphics.Bitmap
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
    val burned = render(source, watermark)
    try {
      destination.outputStream().use {
        if (!burned.compress(Bitmap.CompressFormat.JPEG, 94, it)) throw IOException("无法编码 JPEG")
      }
      // Fresh pixels cannot retain camera GPS/time hidden behind manual overrides.
      writeExif(destination, watermark.content)
    } finally {
      burned.recycle()
    }
  }

  /** The editor uses this same crop and burner at preview resolution. Caller owns the bitmap. */
  fun render(source: File, watermark: PreparedWatermark, maxDimension: Int = 2560): Bitmap {
    val upright = readUprightBitmap(source, maxDimension)
    var framed: Bitmap? = null
    var burned: Bitmap? = null
    try {
      val height = watermark.frame.cropHeight(upright.height)
      framed = if (height == upright.height) upright else Bitmap.createBitmap(upright, 0, 0, upright.width, height)
      burned = BitmapWatermarkBurner().burn(framed, watermark.card)
      return burned
    } finally {
      if (framed !== upright && framed !== burned) framed?.recycle()
      if (upright !== burned) upright.recycle()
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
