package com.nu11b1ade.watermarkcam

import com.nu11b1ade.watermarkcam.domain.PhotoFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PhotoFrameTest {
  @Test fun `saved photo uses the same visible area above camera controls as the live watermark`() {
    val frame = PhotoFrame.aboveControls(previewHeight = 1000, controlsHeight = 200)
    assertEquals(3200, frame.cropHeight(imageHeight = 4000))
    assertEquals(800, frame.cropHeight(imageHeight = 1000))
    assertEquals(4000, PhotoFrame().cropHeight(imageHeight = 4000))
  }

  @Test fun `framing rejects invalid viewports and keeps at least one image pixel`() {
    assertEquals(1, PhotoFrame.aboveControls(1000, 999).cropHeight(10))
    assertThrows(IllegalArgumentException::class.java) { PhotoFrame.aboveControls(0, 0) }
    assertThrows(IllegalArgumentException::class.java) { PhotoFrame.aboveControls(1000, 1000) }
    assertThrows(IllegalArgumentException::class.java) { PhotoFrame.aboveControls(1000, -1) }
    assertThrows(IllegalArgumentException::class.java) { PhotoFrame(Double.NaN) }
    assertThrows(IllegalArgumentException::class.java) { PhotoFrame(1.1) }
    assertThrows(IllegalArgumentException::class.java) { PhotoFrame().cropHeight(0) }
  }
}
