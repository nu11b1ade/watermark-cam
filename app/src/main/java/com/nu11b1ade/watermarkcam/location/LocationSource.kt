package com.nu11b1ade.watermarkcam.location

import com.nu11b1ade.watermarkcam.domain.Coordinates
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow

data class LocationReading(val address: String, val coordinates: Coordinates? = null)

interface LocationSource {
  /** Coordinates arrive independently of addresses, with optional refinements before completion. */
  fun fixes(): Flow<Coordinates>

  suspend fun reverse(coordinates: Coordinates): String?

  /** One-shot settings lookup: use the first usable fix, then obtain its address. */
  suspend fun current(): LocationReading {
    val coordinates = fixes().firstOrNull() ?: return LocationReading("位置暂不可用（可手动设置）")
    return LocationReading(reverse(coordinates) ?: "地址暂不可用（可手动设置）", coordinates)
  }
}

class FixedLocationSource(
  private val reading: LocationReading = LocationReading("位置暂不可用（可手动设置）"),
) : LocationSource {
  override fun fixes(): Flow<Coordinates> = flow { reading.coordinates?.let { emit(it) } }
  override suspend fun reverse(coordinates: Coordinates): String = reading.address
  override suspend fun current(): LocationReading = reading
}
