package com.nu11b1ade.watermarkcam

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.exifinterface.media.ExifInterface
import com.nu11b1ade.watermarkcam.domain.Coordinates
import com.nu11b1ade.watermarkcam.domain.PhotoFrame
import com.nu11b1ade.watermarkcam.domain.WatermarkConfig
import com.nu11b1ade.watermarkcam.domain.WatermarkContent
import com.nu11b1ade.watermarkcam.domain.composeWatermark
import com.nu11b1ade.watermarkcam.history.OriginalsStore
import com.nu11b1ade.watermarkcam.history.PhotoEditState
import com.nu11b1ade.watermarkcam.save.PhotoSaver
import java.io.File
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoSaverTest {
  @get:Rule val temporary = TemporaryFolder()
  private val context = RuntimeEnvironment.getApplication()
  private lateinit var gallery: TestGallery
  private val watermark = composeWatermark(WatermarkConfig(),
    WatermarkContent(ZonedDateTime.parse("2026-10-21T09:08:07+08:00"), "上海", Coordinates(31.2, 121.5)))
    .copy(frame = PhotoFrame(0.75))

  @Before fun registerGallery() {
    gallery = TestGallery(temporary.newFolder("gallery"))
    gallery.attachInfo(context, ProviderInfo().apply { authority = "media" })
    ShadowContentResolver.registerProviderInternal("media", gallery)
  }

  @Test fun `capture stores a clean upright private original and saving an edit only creates a new gallery image`() = runBlocking {
    val raw = cameraJpeg()
    val rawBefore = raw.readBytes()
    val store = OriginalsStore(temporary.newFolder("originals"))
    val saver = PhotoSaver(context, store)
    val uri = saver.saveCaptured(raw, watermark)
    val record = store.list().single()
    assertEquals(uri.toString(), record.galleryUri)
    assertEquals(watermark.content, record.content)
    assertEquals(watermark.config, record.config)
    assertEquals(watermark.frame, record.frame)
    assertArrayEquals(rawBefore, raw.readBytes())
    val original = store.originalFile(record.id)
    val clean = BitmapFactory.decodeFile(original.path)
    try {
      assertEquals(360, clean.width)
      assertEquals(480, clean.height)
      assertTrue("No watermark on the original", (0 until clean.height step 10).all { y ->
        (0 until clean.width step 10).all { x -> Color.red(clean.getPixel(x, y)) < 130 }
      })
    } finally { clean.recycle() }
    val originalBefore = original.readBytes()
    val sidecarBefore = File(original.parentFile, "${record.id}.json").readBytes()
    val oldGallery = gallery.file(uri).readBytes()
    val edited = PhotoEditState.from(record).copy(time = "2024-05-06 07:08:09", place = "新地点", weatherText = "多云 23°C")
    val newUri = saver.saveFromOriginal(original, edited.prepare(record).getOrThrow())
    assertTrue(uri != newUri)
    assertEquals(2, gallery.images.size)
    assertEquals("2024:05:06 07:08:09", ExifInterface(gallery.file(newUri)).getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
    assertArrayEquals(oldGallery, gallery.file(uri).readBytes())
    assertArrayEquals(originalBefore, original.readBytes())
    assertArrayEquals(sidecarBefore, File(original.parentFile, "${record.id}.json").readBytes())
    assertEquals(listOf(record), store.list())
  }

  @Test fun `original storage failure still returns a published gallery photo`() = runBlocking {
    val blocked = temporary.newFile("blocked-originals")
    val saver = PhotoSaver(context, OriginalsStore(blocked))
    val uri = saver.saveCaptured(cameraJpeg(), watermark)
    assertTrue(gallery.file(uri).length() > 0)
    assertEquals(1, gallery.images.size)
  }

  private fun cameraJpeg(): File {
    val raw = temporary.newFile("camera.jpg")
    val bitmap = Bitmap.createBitmap(480, 360, Bitmap.Config.ARGB_8888)
    try {
      bitmap.eraseColor(Color.rgb(90, 180, 220))
      raw.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }
    } finally { bitmap.recycle() }
    ExifInterface(raw).apply {
      setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
      saveAttributes()
    }
    return raw
  }

  private class TestGallery(private val dir: File) : ContentProvider() {
    val images = mutableMapOf<Uri, File>()
    fun file(uri: Uri): File = images.getValue(uri)
    override fun onCreate() = true
    override fun insert(uri: Uri, values: ContentValues?): Uri {
      val id = images.size + 1
      val result = Uri.withAppendedPath(uri, id.toString())
      images[result] = File(dir, "$id.jpg").apply { createNewFile() }
      return result
    }
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
      ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.parseMode(mode))
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 1
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?): Int {
      images.remove(uri)?.delete()
      return 1
    }
    override fun getType(uri: Uri) = "image/jpeg"
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, order: String?): Cursor? = null
  }
}
