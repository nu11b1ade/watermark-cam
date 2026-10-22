package com.nu11b1ade.watermarkcam

import com.nu11b1ade.watermarkcam.domain.Coordinates
import com.nu11b1ade.watermarkcam.location.LocationFix
import com.nu11b1ade.watermarkcam.location.progressiveLocationFixes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressiveLocationFixTest {
  @Test fun `first usable network fix is delivered while GPS is still pending`() = runTest {
    val network = LocationFix(Coordinates(31.0, 121.0), 200f, 100)
    val gps = LocationFix(Coordinates(31.2304, 121.4737), 8f, 1_500)
    val readings = mutableListOf<LocationFix>()
    val collection = launch {
      progressiveLocationFixes(emptyList(), listOf(
        { delay(100); network },
        { delay(1_500); gps },
      )) { testScheduler.currentTime }.toList(readings)
    }

    advanceTimeBy(100)
    runCurrent()

    assertEquals(listOf(network), readings)
    collection.cancelAndJoin()
  }

  @Test fun `better GPS refines the first fix and a later coarser fix cannot replace it`() = runTest {
    val network = LocationFix(Coordinates(31.0, 121.0), 200f, 100)
    val gps = LocationFix(Coordinates(31.2304, 121.4737), 8f, 500)
    val coarser = LocationFix(Coordinates(31.1, 121.1), 300f, 1_000)

    val readings = progressiveLocationFixes(emptyList(), listOf(
      { delay(100); network },
      { delay(500); gps },
      { delay(1_000); coarser },
    )) { testScheduler.currentTime }.toList()

    assertEquals(listOf(network, gps), readings)
  }

  @Test fun `freshest usable last known fix is immediate while fresh requests are pending`() = runTest {
    val cached = LocationFix(Coordinates(31.0, 121.0), 200f, 700_000)
    val older = LocationFix(Coordinates(30.0, 120.0), 8f, 500_000)
    val expired = cached.copy(elapsedRealtimeMillis = 399_999)
    val future = cached.copy(elapsedRealtimeMillis = 1_000_001)
    val readings = mutableListOf<LocationFix>()
    val collection = launch {
      progressiveLocationFixes(listOf(older, expired, cached, future), listOf(
        { delay(5_000); null },
      )) { 1_000_000 + testScheduler.currentTime }.toList(readings)
    }

    runCurrent()

    assertEquals(listOf(cached), readings)
    assertEquals(0L, testScheduler.currentTime)
    collection.cancelAndJoin()
  }

  @Test fun `GPS refinement is cancelled after two seconds and network remains usable`() = runTest {
    val network = LocationFix(Coordinates(31.0, 121.0), 200f, 100)
    var gpsCancelled = false

    val readings = progressiveLocationFixes(emptyList(), listOf(
      { delay(100); network },
      {
        try {
          delay(10_000)
          LocationFix(Coordinates(31.2304, 121.4737), 8f, 10_000)
        } finally {
          gpsCancelled = !currentCoroutineContext().isActive
        }
      },
    )) { testScheduler.currentTime }.toList()

    assertEquals(listOf(network), readings)
    assertEquals(2_000L, testScheduler.currentTime)
    assertTrue(gpsCancelled)
  }
}
