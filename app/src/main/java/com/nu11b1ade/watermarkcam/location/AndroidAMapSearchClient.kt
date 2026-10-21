package com.nu11b1ade.watermarkcam.location

import android.content.Context
import com.amap.api.services.core.AMapException
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.core.ServiceSettings
import com.amap.api.services.geocoder.GeocodeQuery
import com.amap.api.services.geocoder.GeocodeSearch
import com.amap.api.services.geocoder.RegeocodeQuery
import com.amap.api.services.help.Inputtips
import com.amap.api.services.help.InputtipsQuery
import com.amap.api.services.poisearch.PoiSearch
import com.nu11b1ade.watermarkcam.BuildConfig
import com.nu11b1ade.watermarkcam.domain.Coordinates
import com.nu11b1ade.watermarkcam.domain.cleanWatermarkText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeoutOrNull

/** Lazy SDK adapter: constructing the app's geocoder chain does not initialize AMap. */
class AndroidAMapSearchClient(context: Context, private val consent: () -> Boolean?) : AMapSearchClient {
  private val context = context.applicationContext

  fun updatePrivacyConsent(agreed: Boolean) {
    if (!BuildConfig.AMAP_ENABLED) return
    ServiceSettings.updatePrivacyShow(context, true, true)
    ServiceSettings.updatePrivacyAgree(context, agreed)
  }

  override suspend fun suggest(query: String): List<AMapSuggestion> = request {
    val input = InputtipsQuery(query, "").apply { cityLimit = false }
    Inputtips(context, input).requestInputtips().orEmpty().mapNotNull { tip ->
      val name = tip.name?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
      val address = listOfNotNull(tip.district, tip.address).filter { it.isNotBlank() }.distinct().joinToString("")
      AMapSuggestion(name, address, tip.point.toCoordinates(), tip.poiID?.takeIf { it.isNotBlank() })
    }
  }.orEmpty()

  override suspend fun locate(candidate: PlaceCandidate): Coordinates? = request {
    val queryText = listOf(candidate.fullName, candidate.name).distinct().joinToString(" ")
    val poi = try {
      val search = PoiSearch(context, PoiSearch.Query(queryText, "", "").apply { pageSize = 5; cityLimit = false })
      if (!candidate.lookupId.isNullOrBlank()) search.searchPOIId(candidate.lookupId)?.latLonPoint.toCoordinates()
      else search.searchPOI()?.pois.orEmpty().firstOrNull()?.latLonPoint.toCoordinates()
    } catch (_: AMapException) {
      null
    }
    poi ?: GeocodeSearch(context).getFromLocationName(GeocodeQuery(queryText, ""))
      .orEmpty().firstNotNullOfOrNull { it.latLonPoint.toCoordinates() }
  }

  override suspend fun reverse(gcj02: Coordinates): String? = request {
    val query = RegeocodeQuery(LatLonPoint(gcj02.latitude, gcj02.longitude), 200f, GeocodeSearch.AMAP)
    GeocodeSearch(context).getFromLocation(query)?.let { address ->
      cleanWatermarkText(address.formatAddress.orEmpty()).ifBlank {
        listOfNotNull(address.township, address.streetNumber?.street, address.pois?.firstOrNull()?.title)
          .filter { it.isNotBlank() }.distinct().joinToString("")
      }.ifBlank { null }
    }
  }

  private suspend fun <T> request(block: () -> T): T? {
    if (!BuildConfig.AMAP_ENABLED || consent() != true) return null
    return withTimeoutOrNull(6_000) {
      runInterruptible(Dispatchers.IO) {
        // Re-check after dispatch; a user may revoke consent while a lookup is queued.
        val agreed = consent() == true
        updatePrivacyConsent(agreed)
        if (!agreed) return@runInterruptible null
        ServiceSettings.getInstance().apply {
          protocol = ServiceSettings.HTTPS
          connectionTimeOut = 5_000
          soTimeOut = 5_000
        }
        block()
      }
    }
  }

  private fun LatLonPoint?.toCoordinates(): Coordinates? {
    if (this == null || (latitude == 0.0 && longitude == 0.0)) return null
    return runCatching { Coordinates(latitude, longitude) }.getOrNull()
  }
}
