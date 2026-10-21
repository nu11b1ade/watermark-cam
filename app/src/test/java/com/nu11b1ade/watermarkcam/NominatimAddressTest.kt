package com.nu11b1ade.watermarkcam

import com.nu11b1ade.watermarkcam.location.parseNominatimAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NominatimAddressTest {
  @Test fun `Shanghai display name preserves district omitted from address object`() {
    val json = """{
      "display_name":"延安东路, 均乐社区, 南京东路街道, 上海市, 黄浦区, 上海市, 200021, 中国",
      "address":{"city":"上海市","road":"延安东路","country":"中国","country_code":"cn"}
    }"""

    assertEquals("上海市黄浦区南京东路街道均乐社区延安东路", parseNominatimAddress(json))
  }

  @Test fun `Shenzhen display name becomes compact province city district street address`() {
    assertEquals("广东省深圳市福田区莲花街道福中社区金田路", parseNominatimAddress("""{
      "display_name":"金田路, 福中社区, 莲花街道, 福田区, 深圳市, 广东省, 518000, 中国"
    }"""))
  }

  @Test fun `errors malformed responses and empty addresses are unavailable`() {
    listOf(
      """{"error":"Unable to geocode","display_name":"延安东路, 上海市, 中国"}""",
      "not json", "{}", """{"display_name":null}""", """{"display_name":42}""",
      """{"display_name":"   "}""", """{"display_name":"200021, 中国"}""",
    ).forEach { assertNull(it, parseNominatimAddress(it)) }
  }

  @Test fun `non Chinese addresses retain spaces and remove their country name`() {
    assertEquals("Île-de-France Paris Rue de Rivoli", parseNominatimAddress("""{
      "display_name":"Rue de Rivoli, Paris, Île-de-France, 75001, France",
      "address":{"country":"France","country_code":"fr"}
    }"""))
  }

  @Test fun `formatted address passes through watermark whitespace and length cleaning`() {
    val road = "路".repeat(130)
    assertEquals("上海市${"路".repeat(117)}", parseNominatimAddress("""{"display_name":"$road, 上海市, 中国"}"""))
    assertEquals("上海市延安 东路", parseNominatimAddress("""{"display_name":" 延安\n  东路 , 上海市, 中国"}"""))
  }
}
