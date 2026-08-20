package com.ads.sdk.openads

/**
 * Pirago brief: first open requires BOTH flags; later opens only [showOpenAds].
 */
internal object OpenAdsEligibility {
    fun shouldShow(
        isFirstOpen: Boolean,
        showOpenAds: Boolean,
        showOpenAdsFirstOpen: Boolean,
    ): Boolean {
        return if (isFirstOpen) {
            showOpenAds && showOpenAdsFirstOpen
        } else {
            showOpenAds
        }
    }
}
