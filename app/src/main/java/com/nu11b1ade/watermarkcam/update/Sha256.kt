package com.nu11b1ade.watermarkcam.update

import java.io.File
import java.security.MessageDigest

object Sha256 {
  fun verify(file: File, expectedHex: String): Boolean {
    if (!expectedHex.matches(Regex("[0-9a-fA-F]{64}"))) return false
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
      val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
      while (true) {
        val count = input.read(buffer)
        if (count == -1) break
        digest.update(buffer, 0, count)
      }
    }
    val expected = expectedHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    return MessageDigest.isEqual(expected, digest.digest())
  }
}
