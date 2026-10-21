package com.nu11b1ade.watermarkcam.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.nu11b1ade.watermarkcam.domain.Coordinates
import com.nu11b1ade.watermarkcam.domain.cleanWatermarkText
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class LocationReading(val address: String, val coordinates: Coordinates? = null)

interface LocationSource {
  suspend fun current(): LocationReading
}

class FixedLocationSource(
  private val reading: LocationReading = LocationReading("位置暂不可用（可手动设置）"),
) : LocationSource {
  override suspend fun current(): LocationReading = reading
}

class FusedLocationSource(
  private val context: Context,
  private val fallback: LocationSource = FixedLocationSource(),
) : LocationSource {
  @SuppressLint("MissingPermission")
  override suspend fun current(): LocationReading {
    val hasPermission = listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
      .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    if (!hasPermission) return LocationReading("未开启定位（可手动设置）")
    if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) != ConnectionResult.SUCCESS) {
      return fallback.current()
    }
    val cancellation = CancellationTokenSource()
    val location = try {
      withTimeoutOrNull(8_000) {
        LocationServices.getFusedLocationProviderClient(context)
          .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellation.token).await()
      }
    } catch (_: ApiException) {
      null
    } catch (_: SecurityException) {
      null
    } catch (_: IllegalStateException) {
      null
    } finally {
      cancellation.cancel()
    } ?: return fallback.current()
    val coordinates = Coordinates(location.latitude, location.longitude)
    return LocationReading(reverseGeocode(coordinates) ?: "地址暂不可用（可手动设置）", coordinates)
  }

  private suspend fun reverseGeocode(coordinates: Coordinates): String? {
    if (!Geocoder.isPresent()) return null
    val geocoder = Geocoder(context, Locale.SIMPLIFIED_CHINESE)
    return try {
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
          withContext(Dispatchers.IO) {
            @Suppress("DEPRECATION")
            geocoder.getFromLocation(coordinates.latitude, coordinates.longitude, 1)?.firstOrNull()
          }
        }
      }
      address?.let { cleanWatermarkText(it.getAddressLine(0).orEmpty()).ifBlank { null } }
    } catch (_: IOException) {
      null
    } catch (_: IllegalArgumentException) {
      null
    }
  }
}
