package com.nu11b1ade.watermarkcam

import com.nu11b1ade.watermarkcam.domain.Coordinates
import com.nu11b1ade.watermarkcam.domain.formatCoordinate
import com.nu11b1ade.watermarkcam.location.PlaceCandidate
import com.nu11b1ade.watermarkcam.location.parseNominatimSearch
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NominatimSearchTest {
  @Test fun `search parses Chinese candidates and skips entries without valid coordinates or names`() {
    val fullName = "陆家嘴, 浦东新区, 上海市, 200120, 中国"
    val results = parseNominatimSearch("""[
      {"place_id":1234,"lat":"31.239712","lon":"121.499812","display_name":"$fullName"},
      {"lat":"north","lon":"121.5","display_name":"坏坐标"},
      {"lat":"91","lon":"121.5","display_name":"超出纬度"},
      {"lat":"31","lon":"181","display_name":"超出经度"},
      {"lat":"NaN","lon":"121.5","display_name":"非有限值"},
      {"lat":"31","display_name":"缺少经度"},
      {"lat":"31","lon":"121","display_name":""},
      {"lat":"31","lon":"121"}, null
    ]""")

    assertEquals(listOf(PlaceCandidate("上海市 浦东新区 陆家嘴", fullName, Coordinates(31.239712, 121.499812))), results)
  }

  @Test fun `display names accept Chinese commas remove postcodes and country and reverse the first three distinct parts`() {
    val results = parseNominatimSearch("""[
      {"lat":"31.24","lon":"121.50","display_name":"陆家嘴，陆家嘴，200120，浦东新区，上海市，中国"},
      {"lat":"31.23","lon":"121.49","display_name":"东方明珠, 世纪大道, 陆家嘴街道, 浦东新区, 上海市, 中国"},
      {"lat":"31.20","lon":"121.40","display_name":"上海市"}
    ]""")

    assertEquals(listOf("上海市 浦东新区 陆家嘴", "陆家嘴街道 世纪大道 东方明珠", "上海市"), results.map { it.name })
  }

  @Test fun `empty arrays and malformed responses have no candidates`() {
    listOf("[]", "not json", "{}", "[", "[null, 42, {}]").forEach {
      assertEquals(emptyList<PlaceCandidate>(), parseNominatimSearch(it))
    }
  }

  @Test fun `coordinate fill uses four decimals and a decimal point in every locale`() {
    val previous = Locale.getDefault()
    try {
      Locale.setDefault(Locale.GERMANY)
      assertEquals("31.2397", formatCoordinate(31.239712))
      assertEquals("121.5000", formatCoordinate(121.5))
      assertEquals("-33.8600", formatCoordinate(-33.86))
    } finally {
      Locale.setDefault(previous)
    }
  }
}
