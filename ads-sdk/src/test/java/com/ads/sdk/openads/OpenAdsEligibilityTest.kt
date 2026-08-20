package com.ads.sdk.openads

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAdsEligibilityTest {

    @Test
    fun firstOpenRequiresBothFlags() {
        assertTrue(OpenAdsEligibility.shouldShow(true, showOpenAds = true, showOpenAdsFirstOpen = true))
        assertFalse(OpenAdsEligibility.shouldShow(true, showOpenAds = true, showOpenAdsFirstOpen = false))
        assertFalse(OpenAdsEligibility.shouldShow(true, showOpenAds = false, showOpenAdsFirstOpen = true))
        assertFalse(OpenAdsEligibility.shouldShow(true, showOpenAds = false, showOpenAdsFirstOpen = false))
    }

    @Test
    fun laterOpensOnlyNeedShowOpenAds() {
        assertTrue(OpenAdsEligibility.shouldShow(false, showOpenAds = true, showOpenAdsFirstOpen = false))
        assertFalse(OpenAdsEligibility.shouldShow(false, showOpenAds = false, showOpenAdsFirstOpen = true))
    }
}
