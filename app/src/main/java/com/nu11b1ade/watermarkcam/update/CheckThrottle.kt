package com.nu11b1ade.watermarkcam.update

import android.content.SharedPreferences
import androidx.core.content.edit

class CheckThrottle(
  private val preferences: SharedPreferences,
  private val clock: () -> Long = System::currentTimeMillis,
) {
  fun isDue(manual: Boolean = false): Boolean = manual || !preferences.contains(LAST_CHECK) ||
    clock() - preferences.getLong(LAST_CHECK, 0L) >= INTERVAL_MS

  /** Call after an attempt completes, including failures; merely checking isDue never writes. */
  fun recordAttempt() { preferences.edit { putLong(LAST_CHECK, clock()) } }

  private companion object {
    const val LAST_CHECK = "lastCheckAt"
    const val INTERVAL_MS = 24 * 60 * 60 * 1_000L
  }
}
