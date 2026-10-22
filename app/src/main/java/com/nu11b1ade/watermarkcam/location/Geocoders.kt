package com.nu11b1ade.watermarkcam.location

import android.content.Context
import com.nu11b1ade.watermarkcam.BuildConfig
import com.nu11b1ade.watermarkcam.data.SettingsStore

/** Consent is checked per request, including requests from an already-created location source. */
class Geocoders(context: Context) {
  private val app = context.applicationContext
  private val settings = SettingsStore(app)
  private val client = AndroidAMapSearchClient(app, settings::loadAMapConsent)
  private val amap = AMapGeocoder(client,
    configured = BuildConfig.AMAP_ENABLED, consent = settings::loadAMapConsent)

  val amapConsent: Boolean? get() = settings.loadAMapConsent()

  fun setAMapConsent(agreed: Boolean) {
    settings.saveAMapConsent(agreed)
    // Revocation is sent to the SDK immediately; later requests also re-check persisted consent.
    runCatching { client.updatePrivacyConsent(agreed) }
  }

  val forward: ForwardGeocoder = FallbackForwardGeocoder(amap,
    FallbackForwardGeocoder(AndroidForwardGeocoder(app), NominatimForwardGeocoder()))
  /** Camera-open lookups fail soft after AMap / Android; Nominatim is never on this path. */
  val cameraReverse: ReverseGeocoder = FallbackReverseGeocoder(amap, AndroidReverseGeocoder(app))
  val reverse: ReverseGeocoder = FallbackReverseGeocoder(cameraReverse, NominatimReverseGeocoder())
}
