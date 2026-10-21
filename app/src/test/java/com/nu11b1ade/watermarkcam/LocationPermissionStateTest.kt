package com.nu11b1ade.watermarkcam

import com.nu11b1ade.watermarkcam.location.LocationPermissionState
import com.nu11b1ade.watermarkcam.location.locationPermissionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationPermissionStateTest {
  @Test fun `no rationale before the first denial does not mean permanently denied`() {
    assertEquals(LocationPermissionState.CanAsk, locationPermissionState(
      fineGranted = false, coarseGranted = false, hasBeenDenied = false,
      fineRationale = false, coarseRationale = false,
    ))
  }

  @Test fun `a completed denial without either rationale requires settings`() {
    val state = locationPermissionState(false, false, true, false, false)

    assertEquals(LocationPermissionState.PermanentlyDenied, state)
    assertFalse(state.isGranted)
  }

  @Test fun `either location rationale allows another request after a denial`() {
    listOf(true to false, false to true, true to true).forEach { (fine, coarse) ->
      val state = locationPermissionState(false, false, true, fine, coarse)

      assertEquals(LocationPermissionState.CanAsk, state)
      assertFalse(state.isGranted)
    }
  }

  @Test fun `coarse alone is usable even after an earlier denial`() {
    val state = locationPermissionState(false, true, true, false, false)

    assertEquals(LocationPermissionState.ApproximateOnly, state)
    assertTrue(state.isGranted)
  }

  @Test fun `precise permission takes precedence over denial history and approximate permission`() {
    listOf(false, true).forEach { coarse ->
      val state = locationPermissionState(true, coarse, true, false, false)

      assertEquals(LocationPermissionState.Granted, state)
      assertTrue(state.isGranted)
    }
  }
}
