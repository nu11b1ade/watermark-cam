package com.nu11b1ade.watermarkcam.watermark

import android.graphics.Bitmap
import android.graphics.Canvas
import com.nu11b1ade.watermarkcam.domain.WatermarkCard

class BitmapWatermarkBurner {
  /** Returns a mutable bitmap, reusing [source] when possible. Hidden fields leave its pixels intact. */
  fun burn(source: Bitmap, card: WatermarkCard): Bitmap {
    val bitmap = if (source.isMutable) source else checkNotNull(source.copy(Bitmap.Config.ARGB_8888, true))
    WatermarkCardRenderer(card).draw(Canvas(bitmap), bitmap.width.toFloat(), bitmap.height.toFloat())
    return bitmap
  }
}
