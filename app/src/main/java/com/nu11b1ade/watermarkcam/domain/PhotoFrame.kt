package com.nu11b1ade.watermarkcam.domain

import kotlin.math.roundToInt

/** The top-aligned part of the full-screen preview that is not covered by camera controls. */
data class PhotoFrame(val visibleHeightFraction: Double = 1.0) {
  init {
    require(visibleHeightFraction > 0.0 && visibleHeightFraction <= 1.0)
  }

  fun cropHeight(imageHeight: Int): Int {
    require(imageHeight > 0)
    return (imageHeight * visibleHeightFraction).roundToInt().coerceAtLeast(1)
  }

  companion object {
    fun aboveControls(previewHeight: Int, controlsHeight: Int): PhotoFrame {
      require(previewHeight > 0 && controlsHeight in 0 until previewHeight)
      return PhotoFrame((previewHeight - controlsHeight).toDouble() / previewHeight)
    }
  }
}
