package com.nu11b1ade.watermarkcam.domain

/** Keeps an in-progress decimal editable when a numeric keyboard has no minus key. */
fun toggleCoordinateSign(value: String): String {
  val draft = value.trim()
  return if (draft.startsWith('-')) draft.removePrefix("-") else "-${draft.removePrefix("+")}"
}
