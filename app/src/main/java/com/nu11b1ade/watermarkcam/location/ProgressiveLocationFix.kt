package com.nu11b1ade.watermarkcam.location

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Emits cache immediately, then improvements within one shared two-second request window. */
fun progressiveLocationFixes(
  lastKnown: List<LocationFix>,
  requests: List<suspend () -> LocationFix?>,
  nowMillis: () -> Long,
): Flow<LocationFix> = flow {
  var selected = chooseFreshestLastKnownFix(lastKnown, nowMillis())
  selected?.let { emit(it) }
  withTimeoutOrNull(2_000) {
    coroutineScope {
      val results = Channel<LocationFix?>(requests.size)
      requests.forEach { request -> launch { results.send(request()) } }
      repeat(requests.size) {
        val candidate = results.receive()
        val best = chooseBestLocationFix(listOfNotNull(selected, candidate), nowMillis())
        if (best != null && best != selected) {
          selected = best
          emit(best)
        }
      }
    }
  }
}
