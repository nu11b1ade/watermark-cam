package com.nu11b1ade.watermarkcam

import com.nu11b1ade.watermarkcam.domain.Coordinates
import com.nu11b1ade.watermarkcam.location.NominatimReverseGeocoder
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NominatimReverseGeocoderTest {
  @Test fun `reverse requests Chinese JSON with identifying user agent and bounded timeouts`() = runBlocking {
    lateinit var connection: Reply
    val geocoder = NominatimReverseGeocoder { url -> Reply(url).also { connection = it } }

    assertEquals("上海市延安东路", geocoder.reverse(Coordinates(31.2304, 121.4737)))
    assertEquals("https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=31.2304&lon=121.4737&zoom=18&accept-language=zh-CN",
      connection.url.toString())
    assertEquals("GET", connection.requestMethod)
    assertEquals("WatermarkCam/0.2 (personal Android app)", connection.getRequestProperty("User-Agent"))
    assertEquals(5_000, connection.connectTimeout)
    assertEquals(5_000, connection.readTimeout)
  }

  @Test fun `nearby coordinates rounded to four decimals share cached address across instances`() = runBlocking {
    var requests = 0
    val open = { url: URL -> requests++; Reply(url) }

    assertEquals("上海市延安东路", NominatimReverseGeocoder(open).reverse(Coordinates(22.543101, 114.057901)))
    assertEquals("上海市延安东路", NominatimReverseGeocoder(open).reverse(Coordinates(22.543099, 114.057899)))
    assertEquals(1, requests)
  }

  @Test fun `uncached requests from separate instances are spaced at least eleven hundred milliseconds`() = runBlocking {
    val started = mutableListOf<Long>()
    val open = { url: URL -> started.add(System.nanoTime()); Reply(url) }
    NominatimReverseGeocoder(open).reverse(Coordinates(40.0, 116.0))
    NominatimReverseGeocoder(open).reverse(Coordinates(41.0, 116.0))

    assertEquals(2, started.size)
    assertTrue("Process-wide request spacing", started[1] - started[0] >= 1_100_000_000L)
  }

  @Test fun `HTTP network and JSON failures return no address and close the connection`() = runBlocking {
    assertNull(NominatimReverseGeocoder { throw IOException("offline") }.reverse(Coordinates(-31.0, 151.0)))
    val unavailable = Reply(URL("https://nominatim.openstreetmap.org"), status = 429)
    assertNull(NominatimReverseGeocoder { unavailable }.reverse(Coordinates(-32.0, 151.0)))
    assertTrue(unavailable.disconnected)
    val malformed = Reply(URL("https://nominatim.openstreetmap.org"), body = "not json")
    assertNull(NominatimReverseGeocoder { malformed }.reverse(Coordinates(-33.0, 151.0)))
    assertTrue(malformed.disconnected)
  }

  private class Reply(
    url: URL,
    private val body: String = """{"display_name":"延安东路, 上海市, 中国"}""",
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
