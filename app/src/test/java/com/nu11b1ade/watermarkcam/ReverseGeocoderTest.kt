package com.nu11b1ade.watermarkcam

import com.nu11b1ade.watermarkcam.domain.Coordinates
import com.nu11b1ade.watermarkcam.location.FallbackReverseGeocoder
import com.nu11b1ade.watermarkcam.location.ReverseGeocoder
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReverseGeocoderTest {
  @Test fun `null or blank platform addresses use fallback address`() = runTest {
    listOf(null, "", " \n ").forEach { primary ->
      val chain = FallbackReverseGeocoder(ReverseGeocoder { primary }, ReverseGeocoder { "上海市黄浦区" })

      assertEquals("上海市黄浦区", chain.reverse(Coordinates(31.2304, 121.4737)))
    }
  }

  @Test fun `successful platform addresses are cleaned without calling fallback`() = runTest {
    val coordinates = Coordinates(31.2304, 121.4737)
    assertEquals("上海市 黄浦区", FallbackReverseGeocoder(
      ReverseGeocoder { " 上海市\n  黄浦区 " }, ReverseGeocoder { throw AssertionError("Unnecessary network request") },
    ).reverse(coordinates))
  }

  @Test fun `provider errors and empty fallback addresses soft fail`() = runTest {
    val coordinates = Coordinates(31.2304, 121.4737)
    assertEquals("上海", FallbackReverseGeocoder(
      ReverseGeocoder { throw IOException("No platform backend") }, ReverseGeocoder { "上海" },
    ).reverse(coordinates))
    assertNull(FallbackReverseGeocoder(
      ReverseGeocoder { null }, ReverseGeocoder { throw IOException("offline") },
    ).reverse(coordinates))
    assertNull(FallbackReverseGeocoder(ReverseGeocoder { null }, ReverseGeocoder { "  " }).reverse(coordinates))
  }
}
