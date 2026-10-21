package com.nu11b1ade.watermarkcam

import com.nu11b1ade.watermarkcam.domain.Coordinates
import com.nu11b1ade.watermarkcam.location.FallbackForwardGeocoder
import com.nu11b1ade.watermarkcam.location.ForwardGeocoder
import com.nu11b1ade.watermarkcam.location.PlaceCandidate
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForwardGeocoderTest {
  private val candidates = listOf(PlaceCandidate("上海市 浦东新区 陆家嘴", "陆家嘴, 浦东新区, 上海市, 中国", Coordinates(31.2397, 121.4998)))

  @Test fun `successful primary results avoid the fallback`() = runTest {
    val geocoder = FallbackForwardGeocoder(
      ForwardGeocoder { query -> assertEquals("陆家嘴", query); candidates },
      ForwardGeocoder { throw AssertionError("Unnecessary network lookup") },
    )

    assertEquals(candidates, geocoder.search("陆家嘴"))
  }

  @Test fun `empty or failed primary searches try the fallback in order`() = runTest {
    listOf(ForwardGeocoder { emptyList() }, ForwardGeocoder { throw IOException("No platform backend") }).forEach { primary ->
      val calls = mutableListOf<String>()
      val geocoder = FallbackForwardGeocoder(
        ForwardGeocoder { query -> calls.add("primary:$query"); primary.search(query) },
        ForwardGeocoder { query -> calls.add("fallback:$query"); candidates },
      )

      assertEquals(candidates, geocoder.search("陆家嘴"))
      assertEquals(listOf("primary:陆家嘴", "fallback:陆家嘴"), calls)
    }
  }

  @Test fun `network failure soft fails but remains distinguishable from no matches for the UI`() = runTest {
    val offline = FallbackForwardGeocoder(ForwardGeocoder { emptyList() }, ForwardGeocoder { throw IOException("offline") })
    val noMatches = FallbackForwardGeocoder(ForwardGeocoder { emptyList() }, ForwardGeocoder { emptyList() })

    assertEquals(emptyList<PlaceCandidate>(), offline.search("陆家嘴"))
    assertTrue(offline.searchResult("陆家嘴").isFailure)
    assertEquals(emptyList<PlaceCandidate>(), noMatches.searchResult("未知地点").getOrThrow())
  }

  @Test fun `cancelling either geocoder propagates cancellation`() = runTest {
    val cancelled = ForwardGeocoder { throw CancellationException("Left settings") }
    listOf(
      FallbackForwardGeocoder(cancelled, ForwardGeocoder { throw AssertionError("Cancelled lookup must stop") }),
      FallbackForwardGeocoder(ForwardGeocoder { emptyList() }, cancelled),
    ).forEach { geocoder ->
      try {
        geocoder.search("陆家嘴")
        throw AssertionError("Expected cancellation")
      } catch (_: CancellationException) {
        // Cancellation is not a lookup failure.
      }
    }
  }
}
