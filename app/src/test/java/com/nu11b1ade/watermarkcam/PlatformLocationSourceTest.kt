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

  @Test fun `enabled providers race and most accurate recent fix supplies coordinates`() = runTest {
    val context = RuntimeEnvironment.getApplication()
    shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    val manager = context.getSystemService(LocationManager::class.java)
    val platform = shadowOf(manager)
    platform.setLocationEnabled(true)
    platform.setProviderEnabled(LocationManager.GPS_PROVIDER, true)
    platform.setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
    val source = PlatformLocationSource(context, ReverseGeocoder { "上海市黄浦区" })
    val result = async { source.current() }
    runCurrent()
    platform.simulateLocation(fix(LocationManager.NETWORK_PROVIDER, 31.0, 121.0, 200f))
    platform.simulateLocation(fix(LocationManager.GPS_PROVIDER, 31.2304, 121.4737, 8f))
    ShadowLooper.idleMainLooper()

    assertEquals(LocationReading("上海市黄浦区", Coordinates(31.2304, 121.4737)), result.await())
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

  @Test fun `shared ten second timeout falls back to cached fix and cancels platform requests`() = runTest {
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
    assertEquals(10_000, testScheduler.currentTime)
    assertTrue(platform.getLegacyLocationRequests(LocationManager.GPS_PROVIDER).isEmpty())
    assertTrue(platform.getLegacyLocationRequests(LocationManager.NETWORK_PROVIDER).isEmpty())
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

  @Test fun `a provider timeout retains another provider result`() = runTest {
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
  }

  private fun fix(provider: String, latitude: Double, longitude: Double, accuracy: Float) = Location(provider).apply {
    this.latitude = latitude
    this.longitude = longitude
    this.accuracy = accuracy
    elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
    time = System.currentTimeMillis()
  }
}
