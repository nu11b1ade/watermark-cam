package com.nu11b1ade.watermarkcam.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.nu11b1ade.watermarkcam.domain.Coordinates
import com.nu11b1ade.watermarkcam.domain.cleanWatermarkText
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

class PlatformLocationSource(
  private val context: Context,
  private val reverseGeocoder: ReverseGeocoder = Geocoders(context).cameraReverse,
) : LocationSource {
  override suspend fun current(): LocationReading {
    if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) && !hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
      return LocationReading("未开启定位（可手动设置）")
    }
    return super.current()
  }

  @SuppressLint("MissingPermission")
  override fun fixes(): Flow<Coordinates> = flow {
    val fine = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    if (!fine && !hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) return@flow
    val manager = context.getSystemService(LocationManager::class.java) ?: return@flow
    val providers = buildList {
      if (fine) add(LocationManager.GPS_PROVIDER)
      add(LocationManager.NETWORK_PROVIDER)
    }.filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
    val lastKnown = providers.mapNotNull { provider ->
      runCatching { manager.getLastKnownLocation(provider)?.toFix() }.getOrNull()
    }
    emitAll(progressiveLocationFixes(lastKnown, providers.map { provider ->
      suspend { currentFix(manager, provider) }
    }, SystemClock::elapsedRealtime).map { it.coordinates }.distinctUntilChanged())
  }

  override suspend fun reverse(coordinates: Coordinates): String? = try {
    withTimeoutOrNull(3_000) {
      reverseGeocoder.reverse(coordinates)?.let(::cleanWatermarkText)?.ifBlank { null }
    }
  } catch (cancelled: CancellationException) {
    throw cancelled
  } catch (_: Exception) {
    null
  }

  private fun hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

  @SuppressLint("MissingPermission")
  private suspend fun currentFix(manager: LocationManager, provider: String): LocationFix? = try {
    suspendCancellableCoroutine { continuation ->
      val cancellation = CancellationSignal()
      continuation.invokeOnCancellation { cancellation.cancel() }
      LocationManagerCompat.getCurrentLocation(manager, provider, cancellation, { it.run() }) { location ->
        if (continuation.isActive) continuation.resume(location?.toFix())
      }
    }
  } catch (cancelled: CancellationException) {
    throw cancelled
  } catch (_: SecurityException) {
    null
  } catch (_: IllegalArgumentException) {
    null
  } catch (_: IllegalStateException) {
    null
  }

  private fun Location.toFix(): LocationFix? = runCatching {
    LocationFix(Coordinates(latitude, longitude), accuracy.takeIf { hasAccuracy() }, elapsedRealtimeNanos / 1_000_000)
  }.getOrNull()
}
