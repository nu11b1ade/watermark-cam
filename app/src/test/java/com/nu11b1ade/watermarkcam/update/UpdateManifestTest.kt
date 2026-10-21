package com.nu11b1ade.watermarkcam.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UpdateManifestTest {
  @Test fun `parses Chinese notes, ordered APK sources and optional minimum supported version`() {
    val manifest = UpdateManifest.parse("""
      {
        "versionCode": 2,
        "versionName": "0.2.0",
        "notes": "新增应用内更新\n修复问题，提升稳定性",
        "apkUrls": {"gitee": "https://gitee.com/app.apk", "github": "https://github.com/app.apk"},
        "sha256": "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
        "minSupported": 2
      }
    """.trimIndent())!!

    assertEquals(2, manifest.versionCode)
    assertEquals("0.2.0", manifest.versionName)
    assertEquals("新增应用内更新\n修复问题，提升稳定性", manifest.notes)
    assertEquals(listOf("https://github.com/app.apk", "https://gitee.com/app.apk"), manifest.apkUrls.inFetchOrder)
    assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", manifest.sha256)
    assertEquals(2, manifest.minSupported)
  }

  @Test fun `minimum supported version and either APK source may be absent`() {
    val githubOnly = UpdateManifest.parse(UPDATE_JSON)!!
    assertNull(githubOnly.minSupported)
    assertNull(githubOnly.apkUrls.gitee)
    val giteeOnly = JSONObject(UPDATE_JSON).put("apkUrls", JSONObject().put("gitee", "https://gitee.com/app.apk"))
    assertEquals(listOf("https://gitee.com/app.apk"), UpdateManifest.parse(giteeOnly.toString())!!.apkUrls.inFetchOrder)
  }

  @Test fun `rejects invalid checksums, unsafe URLs, absent URLs and invalid required fields`() {
    val invalid = listOf(
      "sha256" to "abc", "sha256" to "g".repeat(64),
      "versionCode" to 0, "versionCode" to -1, "versionCode" to 2.5, "versionCode" to "2",
      "versionCode" to 2147483648L, "versionName" to "", "notes" to 42,
      "minSupported" to -1, "minSupported" to "bad",
      "apkUrls" to JSONObject(),
      "apkUrls" to JSONObject().put("github", "http://github.com/app.apk"),
      "apkUrls" to JSONObject().put("github", "file:///tmp/app.apk"),
      "apkUrls" to JSONObject().put("github", "https:///app.apk"),
      "apkUrls" to JSONObject().put("github", "https://github.com/app.apk").put("gitee", "http://gitee.com/app.apk"),
    )
    invalid.forEach { (field, value) ->
      assertNull("$field=$value", UpdateManifest.parse(JSONObject(UPDATE_JSON).put(field, value).toString()))
    }
    listOf("versionCode", "versionName", "notes", "sha256", "apkUrls").forEach { missing ->
      val json = JSONObject(UPDATE_JSON).apply { remove(missing) }
      assertNull("Missing $missing", UpdateManifest.parse(json.toString()))
    }
    listOf("garbage", "[]", "", "{}").forEach { assertNull(UpdateManifest.parse(it)) }
  }

  @Test fun `normalizes a valid hexadecimal checksum to lowercase`() {
    val json = JSONObject(UPDATE_JSON).put("sha256", ABC_SHA256.uppercase())
    assertEquals(ABC_SHA256, UpdateManifest.parse(json.toString())!!.sha256)
  }
}
