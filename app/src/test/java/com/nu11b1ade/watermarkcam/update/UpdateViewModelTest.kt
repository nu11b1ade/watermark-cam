package com.nu11b1ade.watermarkcam.update

import android.content.Context
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UpdateViewModelTest {
  private val app = RuntimeEnvironment.getApplication()
  private val preferences = app.getSharedPreferences("update-model-test", Context.MODE_PRIVATE)
  private val throttle = CheckThrottle(preferences) { 1_790_000_000_000L }

  @Before fun setUp() {
    preferences.edit().clear().commit()
    Dispatchers.setMain(StandardTestDispatcher())
  }

  @After fun tearDown() { Dispatchers.resetMain() }

  @Test fun `automatic failure is silent and only a completed attempt throttles the next app start`() = runTest {
    val release = CountDownLatch(1)
    val calls = AtomicInteger()
    val checker = UpdateChecker {
      check(release.await(5, TimeUnit.SECONDS))
      calls.incrementAndGet()
      throw IOException("offline")
    }
    val model = model(checker)
    runCurrent()
    assertTrue(throttle.isDue())
    release.countDown()

    val state = model.state.first { it.check != UpdateCheckState.Checking }
    assertEquals(UpdateCheckState.Idle, state.check)
    assertNull(state.dialog)
    assertFalse(throttle.isDue())
    model(checker)
    runCurrent()
    assertEquals(2, calls.get())
  }

  @Test fun `manual check bypasses a recent attempt and exposes failure in settings`() = runTest {
    throttle.recordAttempt()
    val model = model(UpdateChecker { throw IOException("offline") })
    assertEquals(UpdateCheckState.Idle, model.state.value.check)

    model.check(manual = true)

    assertEquals(UpdateCheckState.Failed, model.state.first { it.check == UpdateCheckState.Failed }.check)
    assertFalse(throttle.isDue())
  }

  @Test fun `available automatic update opens an advisory dialog and postponing allows reopening from settings`() = runTest {
    val json = JSONObject(UPDATE_JSON).put("minSupported", 2).toString()
    val model = model(UpdateChecker { json })
    val state = model.state.first { it.check is UpdateCheckState.Available }
    assertEquals("0.2.0", state.dialog!!.manifest.versionName)
    assertTrue(state.dialog.recommendImmediate)

    model.dismissDialog()
    assertNull(model.state.value.dialog)
    model.openUpdate()
    assertEquals(state.dialog, model.state.value.dialog)
  }

  @Test fun `verified download requests install and resumes the same file after granting permission`() = runTest {
    val downloads = AtomicInteger()
    val downloader = ApkDownloader(app.cacheDir) {
      downloads.incrementAndGet()
      ApkResponse(ByteArrayInputStream("abc".toByteArray()), 3)
    }
    val model = model(UpdateChecker { UPDATE_JSON }, downloader)
    model.state.first { it.dialog != null }
    model.downloadUpdate()
    val ready = model.state.first { it.installRequest != null }
    assertEquals("abc", ready.installRequest!!.readText())

    model.onInstallResult(InstallResult.NeedsPermission)
    assertTrue(model.state.value.showInstallPermission)
    assertNull(model.state.value.installRequest)
    model.retryInstall()
    assertFalse(model.state.value.showInstallPermission)
    assertEquals(ready.installRequest, model.state.value.installRequest)
    model.onInstallResult(InstallResult.Started)
    assertNull(model.state.value.dialog)
    assertNull(model.state.value.installRequest)
    assertEquals(1, downloads.get())
  }

  @Test fun `download failure stays in the dialog and does not request installation`() = runTest {
    val downloader = ApkDownloader(app.cacheDir) { ApkResponse(ByteArrayInputStream("corrupt".toByteArray())) }
    val model = model(UpdateChecker { UPDATE_JSON }, downloader)
    model.state.first { it.dialog != null }
    model.downloadUpdate()
    val failed = model.state.first { it.dialog?.download == UpdateDownloadState.Failed }
    assertNull(failed.installRequest)
  }

  private fun model(checker: UpdateChecker, downloader: ApkDownloader = ApkDownloader(app.cacheDir)) =
    UpdateViewModel(app, checker, throttle, downloader, 1)
}
