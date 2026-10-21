package com.nu11b1ade.watermarkcam.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.nu11b1ade.watermarkcam.domain.PreparedWatermark
import com.nu11b1ade.watermarkcam.save.WatermarkedJpegWriter
import com.nu11b1ade.watermarkcam.save.readUprightBitmap
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class OriginalPhoto(val image: ImageBitmap? = null, val failed: Boolean = false)

/** Decoding and burn-in stay off the UI thread; Compose owns only small preview bitmaps. */
@Composable
internal fun rememberOriginalPhoto(file: File, maxDimension: Int, watermark: PreparedWatermark? = null): State<OriginalPhoto> =
  produceState(OriginalPhoto(), file, maxDimension, watermark) {
    value = try {
      withContext(Dispatchers.IO) {
        val bitmap = if (watermark == null) readUprightBitmap(file, maxDimension)
          else WatermarkedJpegWriter().render(file, watermark, maxDimension)
        OriginalPhoto(bitmap.asImageBitmap())
      }
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (_: Exception) {
      OriginalPhoto(failed = true)
    }
  }
