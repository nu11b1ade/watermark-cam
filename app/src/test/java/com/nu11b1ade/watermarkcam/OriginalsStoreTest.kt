package com.nu11b1ade.watermarkcam

import com.nu11b1ade.watermarkcam.domain.PhotoFrame
import com.nu11b1ade.watermarkcam.domain.WatermarkConfig
import com.nu11b1ade.watermarkcam.domain.WatermarkContent
import com.nu11b1ade.watermarkcam.history.OriginalRecord
import com.nu11b1ade.watermarkcam.history.OriginalsStore
import java.io.File
import java.time.ZonedDateTime
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OriginalsStoreTest {
  @get:Rule val temporary = TemporaryFolder()
  private val now = 1792544887000L

  @Test fun `saving copies a private JPEG and sidecar and a new store lists newest first and can delete`() {
    val dir = File(temporary.root, "originals")
    val source = temporary.newFile("raw.jpg").apply { writeBytes(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 42)) }
    val bytes = source.readBytes()
    val store = OriginalsStore(dir) { now }
    assertEquals(emptyList<OriginalRecord>(), store.list())
    val first = record("first", now - 1_000)
    val second = record("second", now)
    store.save(source, first)
    store.save(source, second)
    source.delete()

    val reopened = OriginalsStore(dir) { now }
    assertArrayEquals(bytes, reopened.originalFile("first").readBytes())
    assertEquals(first, reopened.load("first"))
    assertEquals(listOf(second, first), reopened.list())
    assertEquals(setOf("first.jpg", "first.json", "second.jpg", "second.json"), dir.list()!!.toSet())

    reopened.delete("first")
    reopened.delete("first")
    assertNull(reopened.load("first"))
    assertFalse(File(dir, "first.jpg").exists())
    assertFalse(File(dir, "first.json").exists())
    assertEquals(listOf(second), reopened.list())
  }

  @Test fun `list and load skip corrupt sidecars missing JPEGs and mismatched ids`() {
    val dir = temporary.newFolder("originals")
    val source = temporary.newFile("raw.jpg").apply { writeText("jpeg") }
    val store = OriginalsStore(dir) { now }
    val valid = record("valid", now)
    listOf("valid", "corrupt", "missing", "mismatch").forEach { store.save(source, record(it, now)) }
    File(dir, "corrupt.json").writeText("{broken")
    File(dir, "missing.jpg").delete()
    File(dir, "mismatch.json").writeText(File(dir, "valid.json").readText())
    File(dir, "interrupted.tmp").writeText("{partial")

    assertEquals(listOf(valid), store.list())
    listOf("corrupt", "missing", "mismatch", "absent", "../raw").forEach { assertNull(store.load(it)) }
  }

  @Test fun `failed save leaves no partial record or temporary files`() {
    val dir = temporary.newFolder("originals")
    val store = OriginalsStore(dir) { now }
    assertThrows(java.io.IOException::class.java) { store.save(File(temporary.root, "missing.jpg"), record("failed", now)) }
    assertEquals(emptyList<OriginalRecord>(), store.list())
    assertEquals(emptyList<String>(), dir.list()!!.toList())
  }

  @Test fun `7 30 and 90 days keep the exact boundary and remove both files strictly older using the clock`() {
    val source = temporary.newFile("raw.jpg").apply { writeText("jpeg") }
    listOf(7, 30, 90).forEach { days ->
      val dir = temporary.newFolder("days-$days")
      var clock = now
      val store = OriginalsStore(dir) { clock }
      val boundary = now - days * 86_400_000L
      val old = record("old", boundary - 1)
      val exact = record("exact", boundary)
      val recent = record("recent", boundary + 1)
      listOf(old, exact, recent).forEach { store.save(source, it) }

      store.cleanup(days)
      assertEquals(listOf(recent, exact), store.list())
      assertFalse(File(dir, "old.jpg").exists())
      assertFalse(File(dir, "old.json").exists())
      clock += 1
      store.cleanup(days)
      assertEquals(listOf(recent), store.list())
      assertFalse(File(dir, "exact.jpg").exists())
      assertFalse(File(dir, "exact.json").exists())
    }
  }

  @Test fun `forever retains even very old originals and does not create an absent directory`() {
    val dir = File(temporary.root, "originals")
    val store = OriginalsStore(dir) { now }
    store.cleanup(null)
    assertFalse(dir.exists())
    val source = temporary.newFile("raw.jpg").apply { writeText("jpeg") }
    val old = record("old", 0)
    store.save(source, old)
    store.cleanup(null)
    assertEquals(listOf(old), store.list())
  }

  private fun record(id: String, createdAt: Long) = OriginalRecord(id, createdAt, "content://photos/$id",
    WatermarkContent(ZonedDateTime.parse("2020-01-01T09:08:07+08:00"), "上海"), WatermarkConfig(), PhotoFrame())
}
