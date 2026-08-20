package com.ads.sdk.interstitial

import android.app.Activity
import com.ads.sdk.AdsSdk
import com.ads.sdk.FunnelEvent
import com.ads.sdk.InterShowReason
import com.ads.sdk.callback.AdCallback
import com.ads.sdk.callback.AdError
import com.ads.sdk.internal.SdkLog
import com.ads.sdk.revenue.PaidEventMapper
import com.ads.sdk.safeNext
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

class InterstitialAds internal constructor() {

    @Volatile
    private var loaded: InterstitialAd? = null
    @Volatile
    private var loadedUnitId: String? = null
    @Volatile
    private var showing = false

    fun isReady(): Boolean = loaded != null

    fun load(activity: Activity, adUnitId: String, callback: AdCallback? = null) {
        AdsSdk.consent.initializeMobileAds(activity.applicationContext)
        InterstitialAd.load(
            activity,
            adUnitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    attach(ad, adUnitId)
                    AdsSdk.funnel(FunnelEvent.InterApiCalled)
                    callback?.onAdLoaded()
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loaded = null
                    callback?.onAdFailedToLoad(AdError(error.code, error.message))
                }
            },
        )
    }

    fun show(activity: Activity, callback: AdCallback, reason: InterShowReason = InterShowReason.Content) {
        AdsSdk.funnel(FunnelEvent.InterEligible)
        val ignoreInterval = reason != InterShowReason.Content
        if (!AdsSdk.interval.canShow(ignoreInterval)) {
            SdkLog.d("Interstitial skipped: interval ${AdsSdk.interval.remainingMs()}ms left")
            callback.safeNext()
            return
        }
        val ad = loaded
        if (ad == null) {
            callback.onAdFailedToLoad(AdError(message = "interstitial not loaded"))
            callback.safeNext()
            return
        }
        present(activity, ad, callback, reason)
    }

    fun loadAndShow(
        activity: Activity,
        adUnitId: String,
        ignoreInterval: Boolean,
        callback: AdCallback,
        reason: InterShowReason = InterShowReason.Content,
    ) {
        AdsSdk.funnel(FunnelEvent.InterEligible)
        val skipGate = ignoreInterval || reason != InterShowReason.Content
        if (!AdsSdk.interval.canShow(skipGate)) {
            SdkLog.d("Interstitial skipped: interval")
            callback.safeNext()
            return
        }
        loaded?.takeIf { loadedUnitId == adUnitId }?.let { ad ->
            AdsSdk.funnel(FunnelEvent.InterApiCalled)
            present(activity, ad, callback, reason)
            return
        }
        AdsSdk.consent.initializeMobileAds(activity.applicationContext)
        InterstitialAd.load(
            activity,
            adUnitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    attach(ad, adUnitId)
                    AdsSdk.funnel(FunnelEvent.InterApiCalled)
                    callback.onAdLoaded()
                    present(activity, ad, callback, reason)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loaded = null
                    callback.onAdFailedToLoad(AdError(error.code, error.message))
                    callback.safeNext()
                }
            },
        )
    }

    private fun attach(ad: InterstitialAd, adUnitId: String) {
        loaded = ad
        loadedUnitId = adUnitId
        ad.setOnPaidEventListener { value ->
            AdsSdk.paid(
                PaidEventMapper.map(value, ad.responseInfo, "interstitial", adUnitId),
            )
        }
    }

    private fun present(
        activity: Activity,
        ad: InterstitialAd,
        callback: AdCallback,
        reason: InterShowReason,
    ) {
        if (showing) {
            callback.safeNext()
            return
        }
        showing = true
        AdsSdk.appOpen.setShowingFullScreen(true)
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                AdsSdk.funnel(FunnelEvent.InterDisplayed)
                callback.onAdShown()
            }

            override fun onAdImpression() {
                callback.onAdImpression()
            }

            override fun onAdClicked() {
                callback.onAdClicked()
            }

            override fun onAdDismissedFullScreenContent() {
                showing = false
                AdsSdk.appOpen.setShowingFullScreen(false)
                loaded = null
                if (reason == InterShowReason.Content) {
                    AdsSdk.interval.markContentDismissed()
                }
                callback.onAdDismissed()
                callback.safeNext()
            }

            override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                showing = false
                AdsSdk.appOpen.setShowingFullScreen(false)
                loaded = null
                callback.onAdFailedToLoad(AdError(error.code, error.message))
                callback.safeNext()
            }
        }
        ad.show(activity)
    }
}
