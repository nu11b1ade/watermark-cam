package com.nu11b1ade.watermarkcam

import android.content.Context
import androidx.core.content.edit
import com.nu11b1ade.watermarkcam.data.SettingsStore
import com.nu11b1ade.watermarkcam.domain.WatermarkConfig
import com.nu11b1ade.watermarkcam.domain.WatermarkSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

  @Before fun clearSettings() {
    preferences.edit { clear() }
  }

  @Test fun `new installation defaults to medium without custom text`() {
    val config = SettingsStore(context).load()

    assertEquals(WatermarkSize.MEDIUM, config.watermarkSize)
    assertFalse(config.showCustomText)
    assertEquals("", config.customText)
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
}
