package com.nu11b1ade.watermarkcam.location

import com.nu11b1ade.watermarkcam.domain.Coordinates

data class LocationReading(val address: String, val coordinates: Coordinates? = null)

interface LocationSource {
  suspend fun current(): LocationReading
}

class FixedLocationSource(
  private val reading: LocationReading = LocationReading("位置暂不可用（可手动设置）"),
) : LocationSource {
  override suspend fun current(): LocationReading = reading
}
