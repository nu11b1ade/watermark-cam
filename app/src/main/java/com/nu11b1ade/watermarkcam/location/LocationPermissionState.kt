package com.nu11b1ade.watermarkcam.location

enum class LocationPermissionState {
  Granted, ApproximateOnly, CanAsk, PermanentlyDenied;

  val isGranted: Boolean get() = this == Granted || this == ApproximateOnly
}

/** Rationale is also false before the first request, so only a completed denial can be permanent. */
fun locationPermissionState(
  fineGranted: Boolean,
  coarseGranted: Boolean,
  hasBeenDenied: Boolean,
  fineRationale: Boolean,
  coarseRationale: Boolean,
): LocationPermissionState = when {
  fineGranted -> LocationPermissionState.Granted
  coarseGranted -> LocationPermissionState.ApproximateOnly
  hasBeenDenied && !fineRationale && !coarseRationale -> LocationPermissionState.PermanentlyDenied
  else -> LocationPermissionState.CanAsk
}
