package com.nu11b1ade.watermarkcam.history

import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** Private upright JPEGs. The sidecar is the commit marker; readers never see a partial record. */
class OriginalsStore(private val dir: File, private val clock: () -> Long = System::currentTimeMillis) {
  @Synchronized
  fun save(rawJpeg: File, record: OriginalRecord) {
    if (!dir.isDirectory && !dir.mkdirs()) throw IOException("无法创建原图目录")
    val jpeg = originalFile(record.id)
    val json = sidecar(record.id)
    if (jpeg.exists() || json.exists()) throw IOException("原图已存在")
    val jpegTemp = File.createTempFile("original_", ".tmp", dir)
    var jsonTemp: File? = null
    var jpegCommitted = false
    try {
      jsonTemp = File.createTempFile("record_", ".tmp", dir)
      FileOutputStream(jpegTemp).use { output ->
        rawJpeg.inputStream().use { it.copyTo(output) }
        output.fd.sync()
      }
      FileOutputStream(jsonTemp).use { output ->
        output.write(encodeOriginalRecord(record).toByteArray(Charsets.UTF_8))
        output.fd.sync()
      }
      if (!jpegTemp.renameTo(jpeg)) throw IOException("无法保存原图")
      jpegCommitted = true
      if (!jsonTemp.renameTo(json)) throw IOException("无法保存原图记录")
    } catch (error: Exception) {
      if (jpegCommitted) jpeg.delete()
      throw error
    } finally {
      jpegTemp.delete()
      jsonTemp?.delete()
    }
  }

  @Synchronized
  fun list(): List<OriginalRecord> = dir.listFiles().orEmpty()
    .filter { it.extension == "json" }
    .mapNotNull { load(it.nameWithoutExtension) }
    .sortedByDescending { it.createdAt }

  @Synchronized
  fun load(id: String): OriginalRecord? = runCatching {
    requireOriginalId(id)
    if (!originalFile(id).isFile || originalFile(id).length() == 0L) return null
    decodeOriginalRecord(sidecar(id).readText()).also { require(it.id == id) }
  }.getOrNull()

  @Synchronized
  fun delete(id: String) {
    val json = sidecar(id)
    if (json.exists() && !json.delete()) throw IOException("无法删除原图记录")
    val jpeg = originalFile(id)
    if (jpeg.exists() && !jpeg.delete()) throw IOException("无法删除原图")
  }

  @Synchronized
  fun cleanup(retentionDays: Int?) {
    if (retentionDays == null) return
    require(retentionDays > 0)
    val cutoff = clock() - retentionDays * 86_400_000L
    dir.listFiles().orEmpty().filter { it.extension == "json" }.forEach { file ->
      val record = runCatching { decodeOriginalRecord(file.readText()).also { require(it.id == file.nameWithoutExtension) } }.getOrNull()
      if (record != null && record.createdAt < cutoff) delete(record.id)
    }
  }

  fun originalFile(id: String): File {
    requireOriginalId(id)
    return File(dir, "$id.jpg")
  }

  private fun sidecar(id: String): File {
    requireOriginalId(id)
    return File(dir, "$id.json")
  }
}
