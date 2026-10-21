package com.nu11b1ade.watermarkcam.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class Sha256Test {
  @get:Rule val temporary = TemporaryFolder()

  @Test fun `verifies the published SHA-256 abc test vector and rejects changed bytes`() {
    val file = temporary.newFile().apply { writeText("abc") }
    assertTrue(Sha256.verify(file, ABC_SHA256))
    file.appendText("d")
    assertFalse(Sha256.verify(file, ABC_SHA256))
    assertFalse(Sha256.verify(file, "not-a-hash"))
  }
}
