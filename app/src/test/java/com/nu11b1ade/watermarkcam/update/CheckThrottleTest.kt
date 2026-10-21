package com.nu11b1ade.watermarkcam.update

import android.content.Context
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CheckThrottleTest {
  private val preferences = RuntimeEnvironment.getApplication().getSharedPreferences("update-test", Context.MODE_PRIVATE)
  private var now = 1_790_000_000_000L

  @Before fun clearPreferences() { preferences.edit().clear().commit() }

  @Test fun `never checked is due and asking does not record an attempt`() {
    val throttle = CheckThrottle(preferences) { now }
    assertTrue(throttle.isDue())
    assertTrue(CheckThrottle(preferences) { now }.isDue())
  }

  @Test fun `completed attempt persists and is due at exactly 24 hours but not 23 hours`() {
    CheckThrottle(preferences) { now }.recordAttempt()
    now += 23 * 60 * 60 * 1_000L
    assertFalse(CheckThrottle(preferences) { now }.isDue())
    now += 60 * 60 * 1_000L
    assertTrue(CheckThrottle(preferences) { now }.isDue())
  }

  @Test fun `manual check bypasses throttle without changing its persisted timestamp`() {
    val throttle = CheckThrottle(preferences) { now }
    throttle.recordAttempt()
    assertTrue(throttle.isDue(manual = true))
    assertFalse(throttle.isDue())
  }
}
