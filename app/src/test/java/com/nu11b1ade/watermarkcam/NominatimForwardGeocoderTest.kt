package com.nu11b1ade.watermarkcam

import com.nu11b1ade.watermarkcam.domain.Coordinates
import com.nu11b1ade.watermarkcam.location.NominatimForwardGeocoder
import com.nu11b1ade.watermarkcam.location.NominatimReverseGeocoder
import com.nu11b1ade.watermarkcam.location.PlaceCandidate
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NominatimForwardGeocoderTest {
  @Test fun `search encodes the query and requests Chinese candidates in China with identifying headers`() = runBlocking {
    lateinit var connection: Reply
    val geocoder = NominatimForwardGeocoder { url -> Reply(url).also { connection = it } }

    assertEquals(listOf(PlaceCandidate("上海市 浦东新区 陆家嘴", "陆家嘴, 浦东新区, 上海市, 中国", Coordinates(31.2397, 121.4998))),
      geocoder.search("陆家嘴 & 上海"))
    assertEquals("https://nominatim.openstreetmap.org/search?format=jsonv2&q=%E9%99%86%E5%AE%B6%E5%98%B4+%26+%E4%B8%8A%E6%B5%B7&limit=5&accept-language=zh-CN&countrycodes=cn",
      connection.url.toString())
    assertEquals("GET", connection.requestMethod)
    assertEquals("WatermarkCam/0.3 (personal Android app)", connection.getRequestProperty("User-Agent"))
    assertEquals(5_000, connection.connectTimeout)
    assertEquals(5_000, connection.readTimeout)
    assertTrue(connection.disconnected)
  }

  @Test fun `repeated trimmed queries use cached candidates and blank queries skip requests`() = runBlocking {
    val requests = mutableListOf<String>()
    val geocoder = NominatimForwardGeocoder { url -> requests.add(url.query); Reply(url) }
    assertTrue(geocoder.search(" \n ").isEmpty())
    val first = geocoder.search("陆家嘴")
    assertEquals(first, geocoder.search(" 陆家嘴 "))
    assertEquals(1, requests.size)
  }

  @Test fun `forward and reverse requests share process wide spacing including failed requests`() = runBlocking {
    val started = mutableListOf<Long>()
    val forward = NominatimForwardGeocoder { url -> started.add(System.nanoTime()); Reply(url, status = 503) }
    assertTrue(forward.search("陆家嘴限速测试").isEmpty())
    NominatimReverseGeocoder { url ->
      started.add(System.nanoTime())
      Reply(url, body = """{"display_name":"公园, 上海市, 中国"}""")
    }.reverse(Coordinates(30.1234, 120.5678))

    assertEquals(2, started.size)
    assertTrue("Forward and reverse use one request budget", started[1] - started[0] >= 1_100_000_000L)
  }

  @Test fun `network and HTTP failures are not cached and preserve failure status`() = runBlocking {
    var requests = 0
    val unavailable = Reply(URL("https://nominatim.openstreetmap.org"), status = 429)
    val geocoder = NominatimForwardGeocoder { requests++; unavailable }
    assertTrue(geocoder.searchResult("上海").isFailure)
    assertTrue(geocoder.search("上海").isEmpty())
    assertEquals(2, requests)
    assertTrue(unavailable.disconnected)
    assertTrue(NominatimForwardGeocoder { throw IOException("offline") }.searchResult("上海").isFailure)
  }

  @Test fun `cancellation is not swallowed`() = runBlocking {
    try {
      NominatimForwardGeocoder { throw CancellationException("Left settings") }.search("上海")
      throw AssertionError("Expected cancellation")
    } catch (_: CancellationException) {
      // Expected.
    }
  }

  private class Reply(
    url: URL,
    private val body: String = """[{"lat":"31.2397","lon":"121.4998","display_name":"陆家嘴, 浦东新区, 上海市, 中国"}]""",
    private val status: Int = HTTP_OK,
  ) : HttpURLConnection(url) {
    var disconnected = false
    override fun connect() = Unit
    override fun disconnect() { disconnected = true }
    override fun usingProxy() = false
    override fun getResponseCode() = status
    override fun getInputStream() = body.byteInputStream()
  }
}
