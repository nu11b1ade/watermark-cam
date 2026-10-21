package com.nu11b1ade.watermarkcam.location

import com.nu11b1ade.watermarkcam.domain.Coordinates
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Standard Krasovsky GCJ-02 transform. All app-facing coordinates remain WGS-84. */
object CoordinateTransform {
  private const val A = 6378245.0
  private const val EE = 0.00669342162296594323

  fun wgs84ToGcj02(point: Coordinates): Coordinates {
    if (outsideChina(point)) return point
    val x = point.longitude - 105.0
    val y = point.latitude - 35.0
    val radLat = point.latitude / 180.0 * PI
    val magic = 1.0 - EE * sin(radLat) * sin(radLat)
    val root = sqrt(magic)
    val dLat = latitudeOffset(x, y) * 180.0 / ((A * (1.0 - EE)) / (magic * root) * PI)
    val dLng = longitudeOffset(x, y) * 180.0 / (A / root * cos(radLat) * PI)
    return Coordinates(point.latitude + dLat, point.longitude + dLng)
  }

  fun gcj02ToWgs84(point: Coordinates): Coordinates {
    if (outsideChina(point)) return point
    var guess = point
    repeat(20) {
      val projected = wgs84ToGcj02(guess)
      val dLat = projected.latitude - point.latitude
      val dLng = projected.longitude - point.longitude
      if (abs(dLat) < 1e-8 && abs(dLng) < 1e-8) return guess
      guess = Coordinates(guess.latitude - dLat, guess.longitude - dLng)
    }
    return guess
  }

  private fun outsideChina(point: Coordinates): Boolean =
    point.longitude !in 72.004..137.8347 || point.latitude !in 0.8293..55.8271

  private fun latitudeOffset(x: Double, y: Double): Double =
    -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x)) +
      (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0 +
      (20.0 * sin(y * PI) + 40.0 * sin(y / 3.0 * PI)) * 2.0 / 3.0 +
      (160.0 * sin(y / 12.0 * PI) + 320.0 * sin(y * PI / 30.0)) * 2.0 / 3.0

  private fun longitudeOffset(x: Double, y: Double): Double =
    300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x)) +
      (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0 +
      (20.0 * sin(x * PI) + 40.0 * sin(x / 3.0 * PI)) * 2.0 / 3.0 +
      (150.0 * sin(x / 12.0 * PI) + 300.0 * sin(x / 30.0 * PI)) * 2.0 / 3.0
}
