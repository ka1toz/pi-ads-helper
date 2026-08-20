package com.ads.sdk.callback

data class AdError(
    val code: Int = 0,
    val message: String? = null,
)

interface AdCallback {
    /** Always invoked for navigation — show, fail, skip, or timeout. */
    fun onNextAction()

    fun onAdLoaded() {}
    fun onAdFailedToLoad(error: AdError?) {}
    fun onAdShown() {}
    fun onAdImpression() {}
    fun onAdClicked() {}
    fun onAdDismissed() {}
    fun onUserEarnedReward(amount: Int, type: String) {}
}
