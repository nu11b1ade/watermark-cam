package com.nu11b1ade.watermarkcam

import com.nu11b1ade.watermarkcam.domain.Coordinates
import com.nu11b1ade.watermarkcam.location.AMapGeocoder
import com.nu11b1ade.watermarkcam.location.AMapSearchClient
import com.nu11b1ade.watermarkcam.location.AMapSuggestion
import com.nu11b1ade.watermarkcam.location.FallbackForwardGeocoder
import com.nu11b1ade.watermarkcam.location.FallbackReverseGeocoder
import com.nu11b1ade.watermarkcam.location.ForwardGeocoder
import com.nu11b1ade.watermarkcam.location.PlaceCandidate
import com.nu11b1ade.watermarkcam.location.ReverseGeocoder
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AMapGeocoderTest {
  private val gcj = Coordinates(39.910092, 116.403718)

  @Test fun `AMap suggestions expose WGS84 coordinates and Chinese address details`() = runTest {
    val client = FakeClient().apply {
      suggestions = listOf(AMapSuggestion("天安门", "北京市东城区长安街", gcj, "poi-1"))
    }

    val result = AMapGeocoder(client, configured = true, consent = { true }).search("天安门").single()

    assertEquals("天安门", result.name)
    assertEquals("北京市东城区长安街", result.fullName)
    assertEquals(39.908692, result.coordinates!!.latitude, 1e-5)
    assertEquals(116.397477, result.coordinates.longitude, 1e-5)
  }

  @Test fun `missing configuration or unaccepted privacy choice never reaches the SDK`() = runTest {
    listOf(false to true, true to null, true to false).forEach { (configured, consent) ->
      val client = FakeClient()
      val geocoder = AMapGeocoder(client, configured, consent = { consent })

      assertTrue(geocoder.search("朝阳公园").isEmpty())
      assertNull(geocoder.reverse(Coordinates(39.908692, 116.397477)))
      assertNull(geocoder.resolve(PlaceCandidate("朝阳公园", "北京市朝阳区", null)))
      assertTrue(client.calls.isEmpty())
    }
  }

  @Test fun `revoking consent disables an already constructed geocoder`() = runTest {
    var consent = true
    val client = FakeClient()
    val geocoder = AMapGeocoder(client, true, consent = { consent })
    geocoder.search("朝阳公园")
    consent = false
    geocoder.search("奥林匹克公园")
    geocoder.reverse(Coordinates(39.908692, 116.397477))

    assertEquals(listOf("suggest"), client.calls)
  }

  @Test fun `a tip without a point is retained and resolved only when selected`() = runTest {
    val client = FakeClient().apply {
      suggestions = listOf(AMapSuggestion("天安门", "北京市东城区长安街", null, "poi-1"))
      located = gcj
    }
    val geocoder = AMapGeocoder(client, true, consent = { true })
    val tip = geocoder.search("天安门").single()
    assertNull(tip.coordinates)
    assertEquals(listOf("suggest"), client.calls)

    val resolved = geocoder.resolve(tip)!!

    assertEquals("poi-1", client.selected?.lookupId)
    assertEquals("北京市东城区长安街", client.selected?.fullName)
    assertEquals("天安门", resolved.name)
    assertEquals(39.908692, resolved.coordinates!!.latitude, 1e-5)
    assertEquals(116.397477, resolved.coordinates.longitude, 1e-5)
  }

  @Test fun `reverse lookup converts GPS WGS84 to GCJ02 before querying AMap`() = runTest {
    val client = FakeClient().apply { address = "北京市东城区东长安街天安门" }
    val result = AMapGeocoder(client, true, consent = { true }).reverse(Coordinates(39.908692, 116.397477))

    assertEquals("北京市东城区东长安街天安门", result)
    assertEquals(39.910092, client.reversed!!.latitude, 1e-4)
    assertEquals(116.403718, client.reversed!!.longitude, 1e-4)
  }

  @Test fun `failed AMap suggestions and tip resolution use the existing WGS84 fallback`() = runTest {
    val wgs = Coordinates(39.908692, 116.397477)
    val fallback = ForwardGeocoder { listOf(PlaceCandidate("天安门", "北京市东城区", wgs)) }
    val geocoder = FallbackForwardGeocoder(AMapGeocoder(FakeClient().apply { failure = IOException() }, true, { true }), fallback)

    assertEquals(wgs, geocoder.search("天安门").single().coordinates)
    assertEquals(wgs, geocoder.resolve(PlaceCandidate("天安门", "北京市东城区", null))?.coordinates)
  }

  @Test fun `reverse fallback receives the original WGS84 point after an AMap failure`() = runTest {
    val wgs = Coordinates(39.908692, 116.397477)
    var received: Coordinates? = null
    val geocoder = FallbackReverseGeocoder(
      AMapGeocoder(FakeClient().apply { failure = IOException() }, true, { true }),
      ReverseGeocoder { received = it; "系统地址" },
    )

    assertEquals("系统地址", geocoder.reverse(wgs))
    assertEquals(wgs, received)
  }

  @Test(expected = CancellationException::class)
  fun `cancelled AMap search never starts a fallback request`() = runTest {
    FallbackForwardGeocoder(
      AMapGeocoder(FakeClient().apply { failure = CancellationException() }, true, { true }),
      ForwardGeocoder { throw AssertionError("Cancellation must not fall back") },
    ).searchResult("朝阳公园")
  }

  private class FakeClient : AMapSearchClient {
    var suggestions = emptyList<AMapSuggestion>()
    var located: Coordinates? = null
    var address: String? = null
    var selected: PlaceCandidate? = null
    var reversed: Coordinates? = null
    var failure: Exception? = null
    val calls = mutableListOf<String>()
    override suspend fun suggest(query: String): List<AMapSuggestion> {
      calls += "suggest"
      failure?.let { throw it }
      return suggestions
    }
    override suspend fun locate(candidate: PlaceCandidate): Coordinates? {
      calls += "locate"
      selected = candidate
      failure?.let { throw it }
      return located
    }
    override suspend fun reverse(gcj02: Coordinates): String? {
      calls += "reverse"
      reversed = gcj02
      failure?.let { throw it }
      return address
    }
  }
}
