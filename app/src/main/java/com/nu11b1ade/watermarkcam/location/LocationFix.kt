package com.nu11b1ade.watermarkcam.location

import com.nu11b1ade.watermarkcam.domain.Coordinates

/** Monotonic timestamp, in the same elapsed-realtime milliseconds as the selection clock. */
data class LocationFix(val coordinates: Coordinates, val accuracyMeters: Float?, val elapsedRealtimeMillis: Long)

fun chooseBestLocationFix(candidates: List<LocationFix>, nowMillis: Long): LocationFix? =
  candidates.filter { nowMillis - it.elapsedRealtimeMillis in 0L..120_000L }
    .minWithOrNull(compareBy<LocationFix> {
      it.accuracyMeters?.takeIf { accuracy -> accuracy.isFinite() && accuracy >= 0f } ?: Float.POSITIVE_INFINITY
    }.thenByDescending { it.elapsedRealtimeMillis })

fun chooseFreshestLastKnownFix(candidates: List<LocationFix>, nowMillis: Long): LocationFix? =
  candidates.filter { nowMillis - it.elapsedRealtimeMillis in 0L..600_000L }
    .maxByOrNull { it.elapsedRealtimeMillis }
