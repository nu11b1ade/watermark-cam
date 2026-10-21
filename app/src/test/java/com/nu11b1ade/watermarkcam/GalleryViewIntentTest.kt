package com.nu11b1ade.watermarkcam

import android.content.Intent
import android.net.Uri
import com.nu11b1ade.watermarkcam.gallery.GalleryResolver
import com.nu11b1ade.watermarkcam.gallery.galleryViewIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GalleryViewIntentTest {
  private val photo = Uri.parse("content://media/external/images/media/42")

  @Test fun `HyperOS gallery is preferred and receives the readable MediaStore image`() {
    val queries = mutableListOf<Intent>()
    val intent = galleryViewIntent(photo, GalleryResolver {
      queries += Intent(it)
      "com.miui.gallery"
    })

    assertEquals("com.miui.gallery", intent.`package`)
    assertEquals(Intent.ACTION_VIEW, intent.action)
    assertEquals(photo, intent.data)
    assertEquals("image/*", intent.type)
    assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, intent.flags)
    assertEquals(listOf("com.miui.gallery"), queries.map { it.`package` })
  }

  @Test fun `the system default gallery is checked after Xiaomi and must support viewing the photo`() {
    val queries = mutableListOf<Intent>()
    val intent = galleryViewIntent(photo, GalleryResolver {
      queries += Intent(it)
      when {
        it.selector != null -> "org.example.gallery"
        it.`package` == "org.example.gallery" -> "org.example.gallery"
        else -> null
      }
    })

    assertEquals("org.example.gallery", intent.`package`)
    assertEquals(listOf("com.miui.gallery", null, "org.example.gallery"), queries.map { it.`package` })
    assertEquals(Intent.ACTION_MAIN, queries[1].selector?.action)
    assertEquals(setOf(Intent.CATEGORY_APP_GALLERY), queries[1].selector?.categories)
    assertEquals(Intent.ACTION_VIEW, queries[2].action)
    assertEquals(photo, queries[2].data)
    assertEquals("image/*", queries[2].type)
  }

  @Test fun `a default gallery that cannot view images falls back to an unscoped view intent`() {
    val queries = mutableListOf<String?>()
    val intent = galleryViewIntent(photo, GalleryResolver {
      queries += it.`package`
      if (it.selector != null) "org.example.gallery" else null
    })

    assertNull(intent.`package`)
    assertEquals(Intent.ACTION_VIEW, intent.action)
    assertEquals(photo, intent.data)
    assertEquals("image/*", intent.type)
    assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, intent.flags)
    assertEquals(listOf("com.miui.gallery", null, "org.example.gallery"), queries)
  }

  @Test fun `a device without either gallery uses a plain view intent without a chooser`() {
    val intent = galleryViewIntent(photo, GalleryResolver { null })

    assertNull(intent.`package`)
    assertNull(intent.selector)
    assertEquals(Intent.ACTION_VIEW, intent.action)
    assertEquals(photo, intent.data)
    assertEquals("image/*", intent.type)
    assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, intent.flags)
  }
}
