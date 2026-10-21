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
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

class PlatformLocationSource(
  private val context: Context,
  private val reverseGeocoder: ReverseGeocoder = Geocoders(context).reverse,
  private val fallback: LocationSource = FixedLocationSource(),
) : LocationSource {
  @SuppressLint("MissingPermission")
  override suspend fun current(): LocationReading {
    val fine = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    if (!fine && !hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
      return LocationReading("未开启定位（可手动设置）")
    }
    val manager = context.getSystemService(LocationManager::class.java) ?: return fallback.current()
    val providers = buildList {
      if (fine) add(LocationManager.GPS_PROVIDER)
      add(LocationManager.NETWORK_PROVIDER)
    }.filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
    val candidates = mutableListOf<LocationFix>()
    // Keep partial results when another provider does not answer before the shared deadline.
    withTimeoutOrNull(10_000) {
      coroutineScope {
        val results = Channel<LocationFix?>(providers.size)
        providers.forEach { provider -> launch { results.send(currentFix(manager, provider)) } }
        repeat(providers.size) { results.receive()?.let(candidates::add) }
      }
    }
    val fix = chooseBestLocationFix(candidates, SystemClock.elapsedRealtime())
      ?: chooseFreshestLastKnownFix(providers.mapNotNull { provider ->
        runCatching { manager.getLastKnownLocation(provider)?.toFix() }.getOrNull()
      }, SystemClock.elapsedRealtime())
      ?: return fallback.current()
    return LocationReading(reverseGeocoder.reverse(fix.coordinates) ?: "地址暂不可用（可手动设置）", fix.coordinates)
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
