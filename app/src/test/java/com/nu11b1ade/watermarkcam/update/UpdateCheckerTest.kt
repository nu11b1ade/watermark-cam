package com.nu11b1ade.watermarkcam.update

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UpdateCheckerTest {
  private val github = "https://github.com/nu11b1ade/watermark-cam/releases/latest/download/latest.json"
  private val gitee = "https://gitee.com/nullblade/watermark-cam/releases/download/latest/latest.json"

  @Test fun `GitHub is first and a valid manifest never calls Gitee`() = runBlocking {
    val calls = mutableListOf<String>()
    val checker = UpdateChecker { url -> calls.add(url); UPDATE_JSON }

    assertEquals(UpdateCheckResult.Available(UpdateManifest.parse(UPDATE_JSON)!!), checker.check(1))
    assertEquals(listOf(github), calls)
  }

  @Test fun `GitHub failure falls back to rolling latest Gitee release`() = runBlocking {
    val calls = mutableListOf<String>()
    val checker = UpdateChecker { url ->
      calls.add(url)
      if (url == github) throw IOException("offline") else UPDATE_JSON
    }

    assertEquals(UpdateCheckResult.Available(UpdateManifest.parse(UPDATE_JSON)!!), checker.check(1))
    assertEquals(listOf(github, gitee), calls)
  }

  @Test fun `invalid GitHub manifest falls back to Gitee`() = runBlocking {
    val calls = mutableListOf<String>()
    val checker = UpdateChecker { url -> calls.add(url); if (url == github) "{}" else UPDATE_JSON }

    assertEquals(UpdateCheckResult.Available(UpdateManifest.parse(UPDATE_JSON)!!), checker.check(1))
    assertEquals(listOf(github, gitee), calls)
  }

  @Test fun `both failing or invalid sources return Failed`() = runBlocking {
    val calls = mutableListOf<String>()
    val checker = UpdateChecker { url ->
      calls.add(url)
      if (url == github) throw IOException("offline") else "garbage"
    }

    assertEquals(UpdateCheckResult.Failed, checker.check(1))
    assertEquals(listOf(github, gitee), calls)
  }

  @Test fun `same or older manifest returns UpToDate without querying the mirror`() = runBlocking {
    listOf(2, 3).forEach { current ->
      val calls = mutableListOf<String>()
      assertEquals(UpdateCheckResult.UpToDate, UpdateChecker { calls.add(it); UPDATE_JSON }.check(current))
      assertEquals(listOf(github), calls)
    }
  }

  @Test fun `cancellation does not trigger a mirror request`() {
    val calls = mutableListOf<String>()
    assertThrows(CancellationException::class.java) {
      runBlocking { UpdateChecker { calls.add(it); throw CancellationException() }.check(1) }
    }
    assertEquals(listOf(github), calls)
  }
}
