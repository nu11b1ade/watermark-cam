package com.nu11b1ade.watermarkcam.update

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ApkDownloaderTest {
  @get:Rule val temporary = TemporaryFolder()
  private val manifest = UpdateManifest(2, "0.2.0", "修复问题", ApkUrls("https://github.com/app.apk", "https://gitee.com/app.apk"), ABC_SHA256)

  @Test fun `downloads into updates cache, reports bounded progress and publishes only the verified APK`() = runBlocking {
    val calls = mutableListOf<String>()
    val progress = mutableListOf<Float?>()
    val destination = File(temporary.root, "updates/watermark-cam-0.2.0.apk")
    val downloader = ApkDownloader(temporary.root) { url ->
      calls.add(url)
      ApkResponse(ByteArrayInputStream("abc".toByteArray()), 3)
    }

    val file = downloader.download(manifest) {
      assertFalse("Final path must not contain an unverified download", destination.exists())
      progress.add(it)
    }

    assertEquals(destination, file)
    assertEquals("abc", file.readText())
    assertEquals(listOf("https://github.com/app.apk"), calls)
    assertEquals(0f, progress.first())
    assertEquals(1f, progress.last())
    assertTrue(progress.all { it != null && it in 0f..1f })
    assertEquals(listOf(file), file.parentFile!!.listFiles()!!.toList())
  }

  @Test fun `network failure or checksum mismatch falls back to Gitee and discards the bad download`() = runBlocking {
    listOf(false, true).forEach { corrupt ->
      val cache = temporary.newFolder()
      val calls = mutableListOf<String>()
      val downloader = ApkDownloader(cache) { url ->
        calls.add(url)
        if (url == manifest.apkUrls.github && !corrupt) throw IOException("offline")
        val bytes = if (url == manifest.apkUrls.github) "bad APK" else "abc"
        ApkResponse(ByteArrayInputStream(bytes.toByteArray()), bytes.length.toLong())
      }

      val file = downloader.download(manifest)

      assertEquals(listOf("https://github.com/app.apk", "https://gitee.com/app.apk"), calls)
      assertEquals("abc", file.readText())
      assertEquals(listOf(file), file.parentFile!!.listFiles()!!.toList())
    }
  }

  @Test fun `all checksum failures leave no APK or partial file`() {
    val calls = mutableListOf<String>()
    val downloader = ApkDownloader(temporary.root) { url -> calls.add(url); ApkResponse(ByteArrayInputStream("bad".toByteArray())) }

    assertThrows(IOException::class.java) { runBlocking { downloader.download(manifest) } }

    assertEquals(listOf("https://github.com/app.apk", "https://gitee.com/app.apk"), calls)
    assertEquals(emptyList<File>(), File(temporary.root, "updates").listFiles()!!.toList())
  }

  @Test fun `unknown content length reports indeterminate progress and a missing GitHub URL is skipped`() = runBlocking {
    val progress = mutableListOf<Float?>()
    val calls = mutableListOf<String>()
    val downloader = ApkDownloader(temporary.root) { calls.add(it); ApkResponse(ByteArrayInputStream("abc".toByteArray())) }

    val file = downloader.download(manifest.copy(apkUrls = ApkUrls(gitee = manifest.apkUrls.gitee)), progress::add)

    assertEquals("abc", file.readText())
    assertEquals(listOf("https://gitee.com/app.apk"), calls)
    assertTrue(progress.isNotEmpty() && progress.all { it == null })
  }

  @Test fun `cancel closes the response, removes partial data and never requests the mirror`() {
    val calls = mutableListOf<String>()
    var closed = false
    val downloader = ApkDownloader(temporary.root) {
      calls.add(it)
      ApkResponse(ByteArrayInputStream("abc".toByteArray()), 3) { closed = true }
    }

    assertThrows(CancellationException::class.java) {
      runBlocking { downloader.download(manifest) { if (it == 1f) throw CancellationException() } }
    }

    assertTrue(closed)
    assertEquals(listOf("https://github.com/app.apk"), calls)
    assertEquals(emptyList<File>(), File(temporary.root, "updates").listFiles()!!.toList())
  }

  @Test fun `manifest version names cannot escape the updates cache`() = runBlocking {
    val downloader = ApkDownloader(temporary.root) { ApkResponse(ByteArrayInputStream("abc".toByteArray())) }
    val file = downloader.download(manifest.copy(versionName = "../../elsewhere"))
    assertEquals(File(temporary.root, "updates").canonicalFile, file.canonicalFile.parentFile)
  }
}
