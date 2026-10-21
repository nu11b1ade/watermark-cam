package com.nu11b1ade.watermarkcam.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.nu11b1ade.watermarkcam.domain.Coordinates
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

data class PlaceCandidate(val name: String, val fullName: String, val coordinates: Coordinates)

fun interface ForwardGeocoder {
  suspend fun search(query: String): List<PlaceCandidate>

  /** Keeps network failures distinct from no matches for the UI, without changing the soft-failing search. */
  suspend fun searchResult(query: String): Result<List<PlaceCandidate>> = forwardSearchResult { search(query) }
}

internal suspend fun forwardSearchResult(search: suspend () -> List<PlaceCandidate>): Result<List<PlaceCandidate>> = try {
  Result.success(search())
} catch (cancelled: CancellationException) {
  throw cancelled
} catch (error: Exception) {
  Result.failure(error)
}

class FallbackForwardGeocoder(
  private val primary: ForwardGeocoder,
  private val fallback: ForwardGeocoder,
) : ForwardGeocoder {
  override suspend fun search(query: String): List<PlaceCandidate> = searchResult(query).getOrDefault(emptyList())

  override suspend fun searchResult(query: String): Result<List<PlaceCandidate>> {
    if (query.isBlank()) return Result.success(emptyList())
    val result = primary.searchResult(query)
    return if (!result.getOrNull().isNullOrEmpty()) result else fallback.searchResult(query)
  }
}

class AndroidForwardGeocoder(private val context: Context) : ForwardGeocoder {
  override suspend fun search(query: String): List<PlaceCandidate> = try {
    if (query.isBlank() || !Geocoder.isPresent()) emptyList() else {
      val geocoder = Geocoder(context, Locale.SIMPLIFIED_CHINESE)
      val addresses = withTimeoutOrNull(4_000) {
        if (Build.VERSION.SDK_INT >= 33) {
          suspendCancellableCoroutine { continuation ->
            geocoder.getFromLocationName(query, 5, object : Geocoder.GeocodeListener {
              override fun onGeocode(addresses: MutableList<Address>) {
                if (continuation.isActive) continuation.resume(addresses)
              }

              override fun onError(errorMessage: String?) {
                if (continuation.isActive) continuation.resume(emptyList<Address>())
              }
            })
          }
        } else {
          runInterruptible(Dispatchers.IO) {
            @Suppress("DEPRECATION")
            geocoder.getFromLocationName(query, 5).orEmpty()
          }
        }
      }
      addresses.orEmpty().mapNotNull { address ->
        if (!address.hasLatitude() || !address.hasLongitude()) return@mapNotNull null
        val coordinates = try {
          Coordinates(address.latitude, address.longitude)
        } catch (_: IllegalArgumentException) {
          return@mapNotNull null
        }
        val fullName = address.getAddressLine(0)?.trim()?.takeIf { it.isNotBlank() }
          ?: listOfNotNull(address.featureName, address.subLocality, address.locality, address.adminArea).distinct().joinToString(", ")
        val name = shortenPlaceName(fullName).takeIf { it.isNotBlank() } ?: return@mapNotNull null
        PlaceCandidate(name, fullName, coordinates)
      }
    }
  } catch (cancelled: CancellationException) {
    throw cancelled
  } catch (_: Exception) {
    emptyList()
  }
}
