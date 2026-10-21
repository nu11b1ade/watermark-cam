package com.nu11b1ade.watermarkcam.location

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One request budget for all forward/reverse instances, with cache access under the same lock. */
internal object NominatimThrottle {
  private val mutex = Mutex()
  private var lastRequestFinishedNanos: Long? = null

  suspend fun <T> run(cached: () -> T?, request: () -> T): T = mutex.withLock {
    cached()?.let { return@withLock it }
    lastRequestFinishedNanos?.let {
      val remainingNanos = 1_100_000_000L - (System.nanoTime() - it)
      if (remainingNanos > 0) delay((remainingNanos + 999_999) / 1_000_000)
    }
    try {
      request()
    } finally {
      // Failed requests consume the same budget as successful ones.
      lastRequestFinishedNanos = System.nanoTime()
    }
  }
}
