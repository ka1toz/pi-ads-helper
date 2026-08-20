package com.ads.sdk.rewarded

import android.app.Activity
import com.ads.sdk.AdsSdk
import com.ads.sdk.FunnelEvent
import com.ads.sdk.callback.AdCallback
import com.ads.sdk.callback.AdError
import com.ads.sdk.revenue.PaidEventMapper
import com.ads.sdk.safeNext
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

class RewardedAds internal constructor() {

    @Volatile
    private var loaded: RewardedAd? = null

    fun isReady(): Boolean = loaded != null

    fun load(activity: Activity, adUnitId: String, callback: AdCallback? = null) {
        AdsSdk.consent.initializeMobileAds(activity.applicationContext)
        RewardedAd.load(
            activity,
            adUnitId,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    loaded = ad
                    ad.setOnPaidEventListener { value ->
                        AdsSdk.paid(
                            PaidEventMapper.map(value, ad.responseInfo, "rewarded", adUnitId),
                        )
                    }
                    AdsSdk.funnel(FunnelEvent.RewardedApiCalled)
                    callback?.onAdLoaded()
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loaded = null
                    callback?.onAdFailedToLoad(AdError(error.code, error.message))
                }
            },
        )
    }

    fun show(activity: Activity, callback: AdCallback) {
        AdsSdk.funnel(FunnelEvent.RewardedEligible)
        val ad = loaded
        if (ad == null) {
            callback.onAdFailedToLoad(AdError(message = "rewarded not loaded"))
            callback.safeNext()
            return
        }
        AdsSdk.appOpen.setShowingFullScreen(true)
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                AdsSdk.funnel(FunnelEvent.RewardedDisplayed)
                callback.onAdShown()
            }

            override fun onAdDismissedFullScreenContent() {
                AdsSdk.appOpen.setShowingFullScreen(false)
                loaded = null
                callback.onAdDismissed()
                callback.safeNext()
            }

            override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                AdsSdk.appOpen.setShowingFullScreen(false)
                loaded = null
                callback.onAdFailedToLoad(AdError(error.code, error.message))
                callback.safeNext()
            }

            override fun onAdClicked() = callback.onAdClicked()
            override fun onAdImpression() = callback.onAdImpression()
        }
        ad.show(activity) { reward ->
            callback.onUserEarnedReward(reward.amount, reward.type)
        }
    }
}
