package com.nu11b1ade.watermarkcam.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.nu11b1ade.watermarkcam.domain.WatermarkCard
import com.nu11b1ade.watermarkcam.watermark.WatermarkCardRenderer

@Composable
fun WatermarkOverlay(card: WatermarkCard, modifier: Modifier = Modifier) {
  val renderer = remember(card) { WatermarkCardRenderer(card) }
  val description = listOfNotNull(
    card.time?.let { "${it.clock}，${it.date}，${it.weekday}" }, card.place, card.coordinates, card.weather, card.tag,
  ).joinToString("，")
  Canvas(modifier.semantics { contentDescription = description }) {
    drawIntoCanvas { renderer.draw(it.nativeCanvas, size.width, size.height) }
  }
}
