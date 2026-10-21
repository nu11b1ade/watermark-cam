package com.nu11b1ade.watermarkcam

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.provider.Settings
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import com.nu11b1ade.watermarkcam.ui.WatermarkViewModel
import com.nu11b1ade.watermarkcam.update.CheckThrottle
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.android.controller.ActivityController

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class MainActivityPermissionTest {
  @get:Rule val compose = createEmptyComposeRule()
  private val application = RuntimeEnvironment.getApplication()

  @Before fun prepareLaunch() {
    shadowOf(application).denyPermissions(
      Manifest.permission.CAMERA,
      Manifest.permission.ACCESS_FINE_LOCATION,
      Manifest.permission.ACCESS_COARSE_LOCATION,
    )
    application.getSharedPreferences("permissions", Context.MODE_PRIVATE).edit().clear().commit()
    val location = shadowOf(application.getSystemService(LocationManager::class.java))
    location.setProviderEnabled(LocationManager.GPS_PROVIDER, false)
    location.setProviderEnabled(LocationManager.NETWORK_PROVIDER, false)
    // Permission tests must not depend on the update server or start a network request.
    CheckThrottle(application.getSharedPreferences("updates", Context.MODE_PRIVATE)).recordAttempt()
  }

  @Test fun `first launch requests camera and both location permissions together`() = withActivity { controller ->
    assertArrayEquals(
      arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
      shadowOf(controller.get()).lastRequestedPermission?.requestedPermissions,
    )
  }

  @Test fun `a prior camera grant is excluded from the location request`() {
    shadowOf(application).grantPermissions(Manifest.permission.CAMERA)

    withActivity { controller ->
      assertArrayEquals(
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
        shadowOf(controller.get()).lastRequestedPermission?.requestedPermissions,
      )
    }
  }

  @Test fun `approximate location is accepted without an automatic precision upgrade`() {
    shadowOf(application).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)

    withActivity { controller ->
      assertArrayEquals(arrayOf(Manifest.permission.CAMERA), shadowOf(controller.get()).lastRequestedPermission?.requestedPermissions)
    }
  }

  @Test fun `denial does not automatically prompt again on resume or recreation`() = withActivity { controller ->
    val activity = controller.get()
    denyRequest(activity)
    val request = shadowOf(activity).lastRequestedPermission

    repeat(2) {
      controller.pause().resume()
      idleFrames()
      assertSame(request, shadowOf(activity).lastRequestedPermission)
    }
    controller.recreate()
    idleFrames()
    assertNull(shadowOf(controller.get()).lastRequestedPermission)
  }

  @Test fun `permanent location denial is remembered on a fresh launch`() {
    withActivity { denyRequest(it.get()) }

    withActivity { controller ->
      assertArrayEquals(arrayOf(Manifest.permission.CAMERA), shadowOf(controller.get()).lastRequestedPermission?.requestedPermissions)
    }
  }

  @Test fun `approximate grant in the result immediately refreshes the environment`() = withActivity { controller ->
    val activity = controller.get()
    val model = ViewModelProvider(activity)[WatermarkViewModel::class.java]
    assertEquals("未开启定位（可手动设置）", model.state.value.observed.place)
    val request = checkNotNull(shadowOf(activity).lastRequestedPermission)

    shadowOf(application).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
    activity.onRequestPermissionsResult(request.requestCode, request.requestedPermissions,
      request.requestedPermissions.map {
        if (it == Manifest.permission.ACCESS_COARSE_LOCATION) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
      }.toIntArray())
    idleFrames()

    // With providers disabled, this fallback proves a permitted refresh ran instead of leaving the denied state.
    assertEquals("位置暂不可用（可手动设置）", model.state.value.observed.place)
    compose.onNodeWithText("需要位置权限才能显示地点和天气").assertDoesNotExist()
    compose.onNodeWithContentDescription("刷新定位和天气").assertIsDisplayed()
  }

  @Test fun `granting location in settings refreshes the environment on resume`() = withActivity { controller ->
    val activity = controller.get()
    denyRequest(activity)
    controller.pause()
    shadowOf(application).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
    controller.resume()
    idleFrames()

    assertEquals("位置暂不可用（可手动设置）", ViewModelProvider(activity)[WatermarkViewModel::class.java].state.value.observed.place)
    compose.onNodeWithText("需要位置权限才能显示地点和天气").assertDoesNotExist()
    compose.onNodeWithContentDescription("刷新定位和天气").assertIsDisplayed()
  }

  @Test fun `permanent denial opens the app permission settings`() = withActivity { controller ->
    val activity = controller.get()
    denyRequest(activity)

    compose.onNodeWithText("去设置").performClick()

    val intent = shadowOf(activity).nextStartedActivity
    assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
    assertEquals("package:${application.packageName}", intent.dataString)
  }

  private fun denyRequest(activity: MainActivity) {
    val request = checkNotNull(shadowOf(activity).lastRequestedPermission)
    activity.onRequestPermissionsResult(request.requestCode, request.requestedPermissions,
      IntArray(request.requestedPermissions.size) { PackageManager.PERMISSION_DENIED })
    idleFrames()
  }

  private fun withActivity(block: (ActivityController<MainActivity>) -> Unit) {
    val controller = Robolectric.buildActivity(MainActivity::class.java).setup().visible()
    try {
      idleFrames()
      block(controller)
    } finally {
      controller.pause().stop().destroy()
    }
  }

  private fun idleFrames() {
    compose.waitForIdle()
    compose.mainClock.advanceTimeByFrame()
    compose.waitForIdle()
  }
}
