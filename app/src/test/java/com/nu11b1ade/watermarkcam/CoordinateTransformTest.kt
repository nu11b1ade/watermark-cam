package com.nu11b1ade.watermarkcam

import com.nu11b1ade.watermarkcam.domain.Coordinates
import com.nu11b1ade.watermarkcam.location.CoordinateTransform
import org.junit.Assert.assertEquals
import org.junit.Test

class CoordinateTransformTest {
  @Test fun `Tiananmen WGS84 matches the known GCJ02 reference`() {
    val actual = CoordinateTransform.wgs84ToGcj02(Coordinates(39.908692, 116.397477))

    assertEquals(39.910092, actual.latitude, 0.0001)
    assertEquals(116.403718, actual.longitude, 0.0001)
  }

  @Test fun `iterative inverse round trips Chinese reference points within a millionth of a degree`() {
    listOf(
      Coordinates(39.908692, 116.397477), Coordinates(31.2304, 121.4737),
      Coordinates(22.5431, 114.0579), Coordinates(30.5728, 104.0668), Coordinates(43.8256, 87.6168),
    ).forEach { wgs ->
      val roundTrip = CoordinateTransform.gcj02ToWgs84(CoordinateTransform.wgs84ToGcj02(wgs))
      assertEquals(wgs.latitude, roundTrip.latitude, 1e-7)
      assertEquals(wgs.longitude, roundTrip.longitude, 1e-7)
    }
  }

  @Test fun `inverse of the rounded Tiananmen reference projects back accurately`() {
    val gcj = Coordinates(39.910092, 116.403718)
    val wgs = CoordinateTransform.gcj02ToWgs84(gcj)
    assertEquals(39.908692, wgs.latitude, 1e-5)
    assertEquals(116.397477, wgs.longitude, 1e-5)
    val projected = CoordinateTransform.wgs84ToGcj02(wgs)
    assertEquals(gcj.latitude, projected.latitude, 1e-7)
    assertEquals(gcj.longitude, projected.longitude, 1e-7)
  }

  @Test fun `coordinates outside China pass through in both directions`() {
    listOf(Coordinates(51.5074, -0.1278), Coordinates(40.7128, -74.0060), Coordinates(-33.8688, 151.2093)).forEach { point ->
      assertEquals(point, CoordinateTransform.wgs84ToGcj02(point))
      assertEquals(point, CoordinateTransform.gcj02ToWgs84(point))
    }
  }
}
