package com.nu11b1ade.watermarkcam.gallery

import android.content.Intent
import android.net.Uri

fun interface GalleryResolver {
  fun resolvePackage(intent: Intent): String?
}

fun galleryViewIntent(photo: Uri, resolver: GalleryResolver): Intent {
  val view = Intent(Intent.ACTION_VIEW).setDataAndType(photo, "image/*")
    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
  val miui = Intent(view).setPackage("com.miui.gallery")
  if (resolver.resolvePackage(miui) != null) return miui
  val defaultGallery = resolver.resolvePackage(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY))
  if (defaultGallery != null && defaultGallery != "com.miui.gallery") {
    val preferred = Intent(view).setPackage(defaultGallery)
    if (resolver.resolvePackage(preferred) != null) return preferred
  }
  return view
}
