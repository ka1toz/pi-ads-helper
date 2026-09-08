package com.ads.sdk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediationConfigTest {

    @Test
    fun remoteZeroIsMaxOtherwiseAdMob() {
        assertEquals(Mediation.Max, Mediation.fromRemote(0))
        assertEquals(Mediation.AdMob, Mediation.fromRemote(1))
        assertEquals(Mediation.AdMob, Mediation.fromRemote(2))
        assertEquals(Mediation.AdMob, Mediation.fromRemote(-1))
    }

    @Test
    fun openAdFormatParsesPiragoStrings() {
        assertEquals(OpenAdFormat.Off, OpenAdFormat.parse("", OpenAdFormat.Inter))
        assertEquals(OpenAdFormat.Off, OpenAdFormat.parse("   ", OpenAdFormat.AppOpen))
        assertEquals(OpenAdFormat.Inter, OpenAdFormat.parse("inter"))
        assertEquals(OpenAdFormat.AppOpen, OpenAdFormat.parse("aoa"))
        assertEquals(OpenAdFormat.Off, OpenAdFormat.parse("off"))
        assertEquals(OpenAdFormat.Inter, OpenAdFormat.parse("true", OpenAdFormat.Off))
        assertEquals(OpenAdFormat.AppOpen, OpenAdFormat.parse("true", OpenAdFormat.AppOpen))
    }

    @Test
    fun resolvedUnitsMaxDropsNativeAndRewarded() {
        val config = AdsConfig(
            admob = AdmobAdUnits(
                banner = "gma-banner",
                native = "gma-native",
                interstitial = "gma-inter",
                rewarded = "gma-rw",
                appOpen = "gma-aoa",
            ),
            max = MaxAdUnits(
                interstitial = "max-inter",
                appOpen = "max-aoa",
                banner = "max-banner",
                mrec = "max-mrec",
            ),
        )
        val max = ResolvedAdUnits.resolve(config, Mediation.Max)
        assertEquals("max-inter", max.interstitial)
        assertEquals("max-aoa", max.appOpen)
        assertEquals("max-banner", max.banner)
        assertEquals("max-mrec", max.mrec)
        assertTrue(max.native.isEmpty())
        assertTrue(max.rewarded.isEmpty())

        val admob = ResolvedAdUnits.resolve(config, Mediation.AdMob)
        assertEquals("gma-inter", admob.interstitial)
        assertEquals("gma-native", admob.native)
        assertTrue(admob.mrec.isEmpty())
    }

    @Test
    fun resolvedUnitsFallsBackToOpenAdsConfig() {
        val config = AdsConfig(
            openAds = OpenAdsConfig(
                appOpenAdUnitId = "open-aoa",
                interstitialAdUnitId = "open-inter",
            ),
        )
        val units = ResolvedAdUnits.resolve(config, Mediation.AdMob)
        assertEquals("open-inter", units.interstitial)
        assertEquals("open-aoa", units.appOpen)
    }

    @Test
    fun paidEventFromMaxUsesApplovinPlatform() {
        val event = PaidAdEvent.fromMax(1.5, "Meta", "banner", "max-unit")
        assertEquals("AppLovin", event.adPlatform)
        assertEquals("Meta", event.adSource)
        assertEquals(1_500_000L, event.valueMicros)
        assertEquals("USD", event.currencyCode)
    }
}
