package com.nu11b1ade.watermarkcam

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import com.nu11b1ade.watermarkcam.data.SettingsStore
import com.nu11b1ade.watermarkcam.domain.Coordinates
import com.nu11b1ade.watermarkcam.location.FixedLocationSource
import com.nu11b1ade.watermarkcam.location.LocationReading
import com.nu11b1ade.watermarkcam.location.LocationSource
import com.nu11b1ade.watermarkcam.ui.WatermarkViewModel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class WatermarkViewModelTest {
  private val app = RuntimeEnvironment.getApplication()
  private val settings = SettingsStore(app)
  private val models = mutableListOf<WatermarkViewModel>()
  private val dispatcher = StandardTestDispatcher()

  @Before fun setUp() {
    app.getSharedPreferences("watermark", Context.MODE_PRIVATE).edit { clear() }
    Dispatchers.setMain(dispatcher)
  }

  @After fun tearDown() {
    models.toList().forEach { it.leaveForeground() }
    models.clear()
    Dispatchers.resetMain()
  }

  @Test fun `cached automatic location is visible immediately on cold start and permission reentry`() = viewModelTest {
    val coordinates = Coordinates(31.2304, 121.4737)
    settings.saveLastLocation("上海市黄浦区", coordinates)
    val model = model(location = FixedLocationSource(LocationReading("位置暂不可用（可手动设置）")))

    assertEquals("上海市黄浦区", model.state.value.observed.place)
    assertEquals(coordinates, model.state.value.observed.coordinates)

    model.enterForeground(false)
    assertNull(model.state.value.observed.coordinates)
    assertEquals("未开启定位（可手动设置）", model.state.value.observed.place)

    model.enterForeground(true)
    assertEquals("上海市黄浦区", model.state.value.observed.place)
    assertEquals(coordinates, model.state.value.observed.coordinates)
    assertFalse(model.state.value.locating)
  }

  @Test fun `first fix clears locating before reverse finishes and later address replaces placeholder`() = viewModelTest {
    val coordinates = Coordinates(31.0, 121.0)
    val location = object : LocationSource {
      override fun fixes(): Flow<Coordinates> = flow { emit(coordinates) }
      override suspend fun reverse(coordinates: Coordinates): String? {
        delay(5_000)
        return "上海市浦东新区"
      }
    }
    val model = model(location = location)
    model.enterForeground(true)
    runCurrent()

    assertEquals(coordinates, model.state.value.observed.coordinates)
    assertFalse(model.state.value.locating)
    assertNotEquals("上海市浦东新区", model.state.value.observed.place)

    advanceTimeBy(5_000)
    runCurrent()

    assertEquals("上海市浦东新区", model.state.value.observed.place)
    assertEquals(LocationReading("上海市浦东新区", coordinates), settings.loadLastLocation())
  }

  @Test fun `last saved thumbnail uri is restored after process death and cleared when missing`() = viewModelTest {
    val file = File(app.cacheDir, "last-shot.jpg").apply {
      writeBytes(byteArrayOf(1, 2, 3, 4))
    }
    val uri = Uri.fromFile(file)
    settings.saveLastSavedUri(uri)

    val restored = model(location = FixedLocationSource())
    assertEquals(uri, restored.state.value.lastSaved)

    file.delete()
    settings.saveLastSavedUri(uri)
    val cleared = model(location = FixedLocationSource())
    assertNull(cleared.state.value.lastSaved)
    assertNull(settings.loadLastSavedUri())
  }

  private fun model(location: LocationSource) =
    WatermarkViewModel(app, settings, location).also(models::add)

  private fun viewModelTest(block: suspend TestScope.() -> Unit) = runTest(dispatcher) {
    try {
      block()
    } finally {
      models.toList().forEach { it.leaveForeground() }
      models.clear()
    }
  }
}
