package com.nu11b1ade.watermarkcam

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import com.nu11b1ade.watermarkcam.data.SettingsStore
import com.nu11b1ade.watermarkcam.domain.Coordinates
import com.nu11b1ade.watermarkcam.domain.OverrideState
import com.nu11b1ade.watermarkcam.domain.PlaceOverride
import com.nu11b1ade.watermarkcam.domain.WatermarkConfig
import com.nu11b1ade.watermarkcam.domain.WatermarkSize
import com.nu11b1ade.watermarkcam.location.LocationReading
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsStoreTest {
  private val context = RuntimeEnvironment.getApplication()
  private val preferences = context.getSharedPreferences("watermark", Context.MODE_PRIVATE)
  private val overrideKeys = listOf("overrideTime", "time", "overridePlace", "place", "latitude", "longitude")

  @Before fun clearSettings() {
    preferences.edit { clear() }
  }

  @Test fun `AMap consent is undecided on install and persists both acceptance and refusal independently`() {
    assertNull(SettingsStore(context).loadAMapConsent())
    SettingsStore(context).saveAMapConsent(true)
    SettingsStore(context).save(WatermarkConfig(customText = "手动水印"))
    assertEquals(true, SettingsStore(context).loadAMapConsent())
    SettingsStore(context).saveAMapConsent(false)
    assertEquals(false, SettingsStore(context).loadAMapConsent())
  }

  @Test fun `saving overrides keeps only persistent settings across a new store`() {
    val store = SettingsStore(context)
    val automatic = Coordinates(31.2304, 121.4737)
    val uri = Uri.parse("content://media/external/images/media/42")
    val config = WatermarkConfig(
      showTime = false,
      showPlace = false,
      showCoordinates = true,
      showWeather = false,
      showCustomText = true,
      customText = "会话水印",
      watermarkSize = WatermarkSize.LARGE,
      overrides = OverrideState(
        time = LocalDateTime.of(2026, 10, 22, 9, 30),
        place = PlaceOverride("手动地点", Coordinates(30.0, 120.0)),
      ),
    )
    store.saveAMapConsent(true)
    store.saveRetentionDays(30)
    store.saveLastSavedUri(uri)
    store.saveLastLocation("上海市黄浦区", automatic)

    store.save(config)
    val savedOverrideKeys = overrideKeys.filter(preferences::contains)
    val reloaded = SettingsStore(context)

    assertEquals(config.copy(overrides = OverrideState()), reloaded.load())
    assertEquals(emptyList<String>(), savedOverrideKeys)
    assertEquals(true, reloaded.loadAMapConsent())
    assertEquals(30, reloaded.loadRetentionDays())
    assertEquals(uri, reloaded.loadLastSavedUri())
    assertEquals(LocationReading("上海市黄浦区", automatic), reloaded.loadLastLocation())
  }

  @Test fun `loading clears legacy overrides while keeping persistent settings and automatic location`() {
    val store = SettingsStore(context)
    val config = WatermarkConfig(showCoordinates = true, customText = "保留设置")
    val automatic = Coordinates(31.2304, 121.4737)
    store.save(config)
    store.saveLastLocation("上海市黄浦区", automatic)
    preferences.edit {
      putBoolean("overrideTime", true)
      putString("time", "2026-10-22 09:30:00")
      putBoolean("overridePlace", true)
      putString("place", "手动地点")
      putString("latitude", "30.0")
      putString("longitude", "120.0")
    }

    val reloaded = SettingsStore(context)

    assertEquals(config, reloaded.load())
    overrideKeys.forEach { key -> assertFalse("Legacy override key remains: $key", preferences.contains(key)) }
    assertEquals(LocationReading("上海市黄浦区", automatic), reloaded.loadLastLocation())
  }

  @Test fun `last automatic location survives a new store independently of location override`() {
    val store = SettingsStore(context)
    val automatic = Coordinates(31.2304, 121.4737)
    val manual = WatermarkConfig(overrides = OverrideState(place = PlaceOverride("手动地点", Coordinates(30.0, 120.0))))

    assertNull(store.loadLastLocation())
    store.saveLastLocation("上海市黄浦区", automatic)
    store.save(manual)

    assertEquals(LocationReading("上海市黄浦区", automatic), SettingsStore(context).loadLastLocation())
    assertEquals(manual.copy(overrides = OverrideState()), SettingsStore(context).load())
  }

  @Test fun `new installation defaults to medium without custom text`() {
    val config = SettingsStore(context).load()

    assertEquals(WatermarkSize.MEDIUM, config.watermarkSize)
    assertFalse(config.showCoordinates)
    assertFalse(config.showCustomText)
    assertEquals("", config.customText)
  }

  @Test fun `an existing choice to show coordinates persists`() {
    SettingsStore(context).save(WatermarkConfig(showCoordinates = true))
    assertEquals(true, SettingsStore(context).load().showCoordinates)
  }

  @Test fun `every watermark size persists by enum name and survives a new store`() {
    WatermarkSize.entries.forEach { size ->
      val config = WatermarkConfig(watermarkSize = size, showCustomText = true, customText = "平安喜乐")
      SettingsStore(context).save(config)

      assertEquals(size.name, preferences.getString("watermarkSize", null))
      assertEquals(config, SettingsStore(context).load())
    }
  }

  @Test fun `missing or invalid watermark size falls back to medium`() {
    listOf(null, "", "HUGE", "medium").forEach { stored ->
      preferences.edit { putString("watermarkSize", stored) }

      assertEquals(WatermarkSize.MEDIUM, SettingsStore(context).load().watermarkSize)
    }
  }

  @Test fun `original retention defaults to 7 days and all choices persist independently of watermark config`() {
    assertEquals(7, SettingsStore(context).loadRetentionDays())
    listOf(7, 30, 90, null).forEach { days ->
      SettingsStore(context).saveRetentionDays(days)
      SettingsStore(context).save(WatermarkConfig(customText = "保留原图"))
      assertEquals(days, SettingsStore(context).loadRetentionDays())
    }
  }

  @Test fun `invalid persisted retention falls back to 7 days`() {
    preferences.edit { putInt("originalRetentionDays", 999) }
    assertEquals(7, SettingsStore(context).loadRetentionDays())
  }

  @Test fun `last saved photo uri persists and can be cleared`() {
    val store = SettingsStore(context)
    val uri = Uri.parse("content://media/external/images/media/42")
    assertNull(store.loadLastSavedUri())
    store.saveLastSavedUri(uri)
    store.save(WatermarkConfig(customText = "缩略图"))
    assertEquals(uri, SettingsStore(context).loadLastSavedUri())
    store.saveLastSavedUri(null)
    assertNull(SettingsStore(context).loadLastSavedUri())
  }
}
