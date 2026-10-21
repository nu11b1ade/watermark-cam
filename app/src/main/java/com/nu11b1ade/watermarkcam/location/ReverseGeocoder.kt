package com.nu11b1ade.watermarkcam.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.nu11b1ade.watermarkcam.domain.Coordinates
import com.nu11b1ade.watermarkcam.domain.cleanWatermarkText
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

fun interface ReverseGeocoder {
  suspend fun reverse(coordinates: Coordinates): String?
}

class FallbackReverseGeocoder(
  private val primary: ReverseGeocoder,
  private val fallback: ReverseGeocoder,
) : ReverseGeocoder {
  override suspend fun reverse(coordinates: Coordinates): String? =
    addressFrom(primary, coordinates) ?: addressFrom(fallback, coordinates)

  private suspend fun addressFrom(geocoder: ReverseGeocoder, coordinates: Coordinates): String? = try {
    geocoder.reverse(coordinates)?.let { cleanWatermarkText(it).ifBlank { null } }
  } catch (cancelled: CancellationException) {
    throw cancelled
  } catch (_: Exception) {
    null
  }
}

class AndroidReverseGeocoder(private val context: Context) : ReverseGeocoder {
  override suspend fun reverse(coordinates: Coordinates): String? = try {
    if (!Geocoder.isPresent()) null else {
      val geocoder = Geocoder(context, Locale.SIMPLIFIED_CHINESE)
      val address = withTimeoutOrNull(4_000) {
        if (Build.VERSION.SDK_INT >= 33) {
          suspendCancellableCoroutine { continuation ->
            geocoder.getFromLocation(coordinates.latitude, coordinates.longitude, 1, object : Geocoder.GeocodeListener {
              override fun onGeocode(addresses: MutableList<Address>) {
                if (continuation.isActive) continuation.resume(addresses.firstOrNull())
              }

              override fun onError(errorMessage: String?) {
                if (continuation.isActive) continuation.resume(null)
              }
            })
          }
        } else {
          runInterruptible(Dispatchers.IO) {
            @Suppress("DEPRECATION")
            geocoder.getFromLocation(coordinates.latitude, coordinates.longitude, 1)?.firstOrNull()
          }
        }
      }
      address?.let { cleanWatermarkText(it.getAddressLine(0).orEmpty()).ifBlank { null } }
    }
  } catch (cancelled: CancellationException) {
    throw cancelled
  } catch (_: Exception) {
    null
  }
}
