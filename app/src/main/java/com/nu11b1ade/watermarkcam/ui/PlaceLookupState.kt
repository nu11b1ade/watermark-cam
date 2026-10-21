package com.nu11b1ade.watermarkcam.ui

import com.nu11b1ade.watermarkcam.domain.Coordinates
import com.nu11b1ade.watermarkcam.location.PlaceCandidate

enum class PlaceLookupTrigger { DEBOUNCE, BUTTON, PICK }

data class PlaceCoordinatesDecision(val coordinatesToFill: Coordinates?, val manuallyEdited: Boolean)

/** A null fill leaves the draft untouched; explicit actions opt back into automatic coordinates. */
fun placeCoordinatesAfterLookup(
  trigger: PlaceLookupTrigger,
  manuallyEdited: Boolean,
  results: List<PlaceCandidate>,
): PlaceCoordinatesDecision {
  val protectManualCoordinates = trigger == PlaceLookupTrigger.DEBOUNCE && manuallyEdited
  return PlaceCoordinatesDecision(
    coordinatesToFill = if (protectManualCoordinates) null else results.firstOrNull()?.coordinates,
    manuallyEdited = protectManualCoordinates,
  )
}
