package com.nu11b1ade.watermarkcam.location

import com.nu11b1ade.watermarkcam.domain.Coordinates

/** Only this SDK-facing interface uses GCJ-02; callers and saved photos use WGS-84. */
data class AMapSuggestion(val name: String, val address: String, val gcj02: Coordinates?, val poiId: String? = null)

interface AMapSearchClient {
  suspend fun suggest(query: String): List<AMapSuggestion>
  suspend fun locate(candidate: PlaceCandidate): Coordinates?
  suspend fun reverse(gcj02: Coordinates): String?
}

class AMapGeocoder(
  private val client: AMapSearchClient,
  private val configured: Boolean,
  private val consent: () -> Boolean?,
) : ForwardGeocoder, ReverseGeocoder {
  private val available: Boolean get() = configured && consent() == true

  override suspend fun search(query: String): List<PlaceCandidate> {
    if (!available || query.trim().length < 2) return emptyList()
    return client.suggest(query.trim()).take(5).map { tip ->
      PlaceCandidate(tip.name, tip.address.ifBlank { tip.name }, tip.gcj02?.let(CoordinateTransform::gcj02ToWgs84), tip.poiId)
    }
  }

  override suspend fun resolve(candidate: PlaceCandidate): PlaceCandidate? {
    if (candidate.coordinates != null) return candidate
    if (!available) return null
    return client.locate(candidate)?.let { candidate.copy(coordinates = CoordinateTransform.gcj02ToWgs84(it)) }
  }

  override suspend fun reverse(coordinates: Coordinates): String? =
    if (!available) null else client.reverse(CoordinateTransform.wgs84ToGcj02(coordinates))
}
