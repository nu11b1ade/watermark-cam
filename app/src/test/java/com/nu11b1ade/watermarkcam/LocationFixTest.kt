package com.nu11b1ade.watermarkcam

import com.nu11b1ade.watermarkcam.domain.Coordinates
import com.nu11b1ade.watermarkcam.location.LocationFix
import com.nu11b1ade.watermarkcam.location.chooseBestLocationFix
import com.nu11b1ade.watermarkcam.location.chooseFreshestLastKnownFix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocationFixTest {
  @Test fun `most accurate recent fix wins even if another provider replied later`() {
    val gps = LocationFix(Coordinates(31.2304, 121.4737), 8f, 980_000)
    val network = LocationFix(Coordinates(31.23, 121.47), 100f, 999_000)

    assertEquals(gps, chooseBestLocationFix(listOf(network, gps), nowMillis = 1_000_000))
  }

  @Test fun `stale and future fixes cannot beat a recent fix`() {
    val recent = LocationFix(Coordinates(31.23, 121.47), 100f, 880_000)
    val stale = LocationFix(Coordinates(30.0, 120.0), 1f, 879_999)
    val future = LocationFix(Coordinates(32.0, 122.0), 1f, 1_000_001)

    assertEquals(recent, chooseBestLocationFix(listOf(stale, future, recent), nowMillis = 1_000_000))
    assertNull(chooseBestLocationFix(listOf(stale, future), nowMillis = 1_000_000))
  }

  @Test fun `equal accuracy prefers freshest and missing or invalid accuracy never wins`() {
    val older = LocationFix(Coordinates(31.0, 121.0), 8f, 980_000)
    val newest = LocationFix(Coordinates(31.23, 121.47), 8f, 999_000)
    val unknown = newest.copy(accuracyMeters = null)
    val invalid = newest.copy(accuracyMeters = -1f)
    val notANumber = newest.copy(accuracyMeters = Float.NaN)

    assertEquals(newest, chooseBestLocationFix(listOf(older, unknown, invalid, notANumber, newest), 1_000_000))
    assertEquals(unknown, chooseBestLocationFix(listOf(unknown), 1_000_000))
    assertNull(chooseBestLocationFix(emptyList(), 1_000_000))
  }

  @Test fun `last known fallback prefers freshness and expires after ten minutes`() {
    val oldestAllowed = LocationFix(Coordinates(31.0, 121.0), 1f, 400_000)
    val freshest = LocationFix(Coordinates(31.23, 121.47), 200f, 700_000)
    val expired = oldestAllowed.copy(elapsedRealtimeMillis = 399_999)
    val future = freshest.copy(elapsedRealtimeMillis = 1_000_001)

    assertEquals(freshest, chooseFreshestLastKnownFix(listOf(freshest, oldestAllowed, expired, future), 1_000_000))
    assertEquals(oldestAllowed, chooseFreshestLastKnownFix(listOf(oldestAllowed), 1_000_000))
    assertNull(chooseFreshestLastKnownFix(listOf(expired, future), 1_000_000))
  }
}
