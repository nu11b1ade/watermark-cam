package com.nu11b1ade.watermarkcam

import android.Manifest
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import com.nu11b1ade.watermarkcam.domain.Coordinates
import com.nu11b1ade.watermarkcam.location.LocationReading
import com.nu11b1ade.watermarkcam.location.PlatformLocationSource
import com.nu11b1ade.watermarkcam.location.ReverseGeocoder
import java.time.Duration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
@OptIn(ExperimentalCoroutinesApi::class)
class PlatformLocationSourceTest {
  @Test fun `missing location permission preserves manual override prompt`() = runTest {
    val context = RuntimeEnvironment.getApplication()
    shadowOf(context).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

    assertEquals(LocationReading("未开启定位（可手动设置）"), PlatformLocationSource(context).current())
  }

  @Test fun `network is shown before GPS and better GPS can refine without reverse geocoding`() = runTest {
    val context = RuntimeEnvironment.getApplication()
    shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    val manager = context.getSystemService(LocationManager::class.java)
    val platform = shadowOf(manager)
    platform.setLocationEnabled(true)
    platform.setProviderEnabled(LocationManager.GPS_PROVIDER, true)
    platform.setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
    val source = PlatformLocationSource(context, ReverseGeocoder { throw AssertionError("Fixes must not wait for addresses") })
    val readings = mutableListOf<Coordinates>()
    val collection = launch { source.fixes().toList(readings) }
    runCurrent()
    platform.simulateLocation(fix(LocationManager.NETWORK_PROVIDER, 31.0, 121.0, 200f))
    ShadowLooper.idleMainLooper()
    runCurrent()

    assertEquals(listOf(Coordinates(31.0, 121.0)), readings)

    platform.simulateLocation(fix(LocationManager.GPS_PROVIDER, 31.2304, 121.4737, 8f))
    ShadowLooper.idleMainLooper()
    collection.join()

    assertEquals(listOf(Coordinates(31.0, 121.0), Coordinates(31.2304, 121.4737)), readings)
  }

  @Test fun `coarse only permission uses network and retains coordinates when address is unavailable`() = runTest {
    val context = RuntimeEnvironment.getApplication()
    shadowOf(context).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    shadowOf(context).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
    val platform = shadowOf(context.getSystemService(LocationManager::class.java))
    platform.setLocationEnabled(true)
    platform.setProviderEnabled(LocationManager.GPS_PROVIDER, true)
    platform.setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
    platform.simulateLocation(fix(LocationManager.GPS_PROVIDER, 31.2304, 121.4737, 8f))
    platform.simulateLocation(fix(LocationManager.NETWORK_PROVIDER, 31.0, 121.0, 200f))

    val reading = PlatformLocationSource(context, ReverseGeocoder { null }).current()

    assertEquals(LocationReading("地址暂不可用（可手动设置）", Coordinates(31.0, 121.0)), reading)
  }

  @Test fun `disabled providers cannot supply even a cached fix`() = runTest {
    val context = RuntimeEnvironment.getApplication()
    shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    val platform = shadowOf(context.getSystemService(LocationManager::class.java))
    platform.setLocationEnabled(true)
    platform.setProviderEnabled(LocationManager.GPS_PROVIDER, false)
    platform.setProviderEnabled(LocationManager.NETWORK_PROVIDER, false)
    platform.simulateLocation(fix(LocationManager.GPS_PROVIDER, 31.2304, 121.4737, 8f))

    assertEquals(LocationReading("位置暂不可用（可手动设置）"), PlatformLocationSource(context).current())
  }

  @Test fun `usable last known fix completes settings lookup immediately without fresh requests`() = runTest {
    ShadowSystemClock.advanceBy(Duration.ofMinutes(20))
    val context = RuntimeEnvironment.getApplication()
    shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    val platform = shadowOf(context.getSystemService(LocationManager::class.java))
    platform.setLocationEnabled(true)
    platform.setProviderEnabled(LocationManager.GPS_PROVIDER, true)
    platform.setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
    platform.simulateLocation(fix(LocationManager.GPS_PROVIDER, 31.2304, 121.4737, 8f).apply {
      elapsedRealtimeNanos -= 5 * 60_000_000_000L
    })

    val result = async { PlatformLocationSource(context, ReverseGeocoder { "上海" }).current() }

    assertEquals(LocationReading("上海", Coordinates(31.2304, 121.4737)), result.await())
    assertEquals(0L, testScheduler.currentTime)
    assertTrue(platform.getLegacyLocationRequests(LocationManager.GPS_PROVIDER).isEmpty())
    assertTrue(platform.getLegacyLocationRequests(LocationManager.NETWORK_PROVIDER).isEmpty())
  }

  @Test fun `fresh wait ends after two seconds and removes all platform requests`() = runTest {
    val context = RuntimeEnvironment.getApplication()
    shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    val platform = shadowOf(context.getSystemService(LocationManager::class.java))
    platform.setLocationEnabled(true)
    platform.setProviderEnabled(LocationManager.GPS_PROVIDER, true)
    platform.setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
    val source = PlatformLocationSource(context, ReverseGeocoder { throw AssertionError("No fix to geocode") })

    assertEquals(LocationReading("位置暂不可用（可手动设置）"), source.current())
    assertEquals(2_000L, testScheduler.currentTime)
    assertTrue(platform.getLegacyLocationRequests(LocationManager.GPS_PROVIDER).isEmpty())
    assertTrue(platform.getLegacyLocationRequests(LocationManager.NETWORK_PROVIDER).isEmpty())
  }

  @Test fun `settings lookup keeps coordinates and soft fails when reverse exceeds its budget`() = runTest {
    val context = RuntimeEnvironment.getApplication()
    shadowOf(context).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
    val platform = shadowOf(context.getSystemService(LocationManager::class.java))
    platform.setLocationEnabled(true)
    platform.setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
    platform.simulateLocation(fix(LocationManager.NETWORK_PROVIDER, 31.0, 121.0, 200f))
    val source = PlatformLocationSource(context, ReverseGeocoder { delay(30_000); null })

    assertEquals(LocationReading("地址暂不可用（可手动设置）", Coordinates(31.0, 121.0)), source.current())
    assertEquals(3_000L, testScheduler.currentTime)
  }

  @Test fun `caller cancellation removes all platform requests`() = runTest {
    val context = RuntimeEnvironment.getApplication()
    shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    val platform = shadowOf(context.getSystemService(LocationManager::class.java))
    platform.setLocationEnabled(true)
    platform.setProviderEnabled(LocationManager.GPS_PROVIDER, true)
    platform.setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
    val source = PlatformLocationSource(context, ReverseGeocoder { throw AssertionError("Cancelled request must not geocode") })
    val cancelled = async { source.current() }
    runCurrent()
    cancelled.cancelAndJoin()
    assertTrue(platform.getLegacyLocationRequests(LocationManager.GPS_PROVIDER).isEmpty())
    assertTrue(platform.getLegacyLocationRequests(LocationManager.NETWORK_PROVIDER).isEmpty())
  }

  @Test fun `settings lookup uses first network fix without waiting for GPS`() = runTest {
    val context = RuntimeEnvironment.getApplication()
    shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    val platform = shadowOf(context.getSystemService(LocationManager::class.java))
    platform.setLocationEnabled(true)
    platform.setProviderEnabled(LocationManager.GPS_PROVIDER, true)
    platform.setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
    val source = PlatformLocationSource(context, ReverseGeocoder { "上海" })
    val result = async { source.current() }
    runCurrent()
    platform.simulateLocation(fix(LocationManager.NETWORK_PROVIDER, 31.0, 121.0, 200f))
    ShadowLooper.idleMainLooper()

    assertEquals(LocationReading("上海", Coordinates(31.0, 121.0)), result.await())
    assertEquals(0L, testScheduler.currentTime)
    assertTrue(platform.getLegacyLocationRequests(LocationManager.GPS_PROVIDER).isEmpty())
    assertTrue(platform.getLegacyLocationRequests(LocationManager.NETWORK_PROVIDER).isEmpty())
  }

  private fun fix(provider: String, latitude: Double, longitude: Double, accuracy: Float) = Location(provider).apply {
    this.latitude = latitude
    this.longitude = longitude
    this.accuracy = accuracy
    elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
    time = System.currentTimeMillis()
  }
}
