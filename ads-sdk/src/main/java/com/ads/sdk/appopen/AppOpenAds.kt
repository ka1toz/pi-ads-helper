package com.ads.sdk.appopen

import android.app.Activity
import com.ads.sdk.AdsSdk
import com.ads.sdk.InterShowReason
import com.ads.sdk.ResumeFormat
import com.ads.sdk.callback.AdCallback
import com.ads.sdk.callback.AdError
import com.ads.sdk.internal.SdkLog
import com.ads.sdk.revenue.PaidEventMapper
import com.ads.sdk.safeNext
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import java.util.Collections

class AppOpenAds internal constructor() {

    private val excluded = Collections.synchronizedSet(mutableSetOf<Class<out Activity>>())
    @Volatile
    private var globallyEnabled = true
    @Volatile
    private var showingFullScreen = false
    @Volatile
    private var loaded: AppOpenAd? = null
    @Volatile
    private var loadedAt: Long = 0L
    @Volatile
    private var showing = false

    fun disableResumeWith(activityClass: Class<out Activity>) {
        excluded.add(activityClass)
    }

    fun enableResumeWith(activityClass: Class<out Activity>) {
        excluded.remove(activityClass)
    }

    fun disableResume() {
        globallyEnabled = false
    }

    fun enableResume() {
        globallyEnabled = true
    }

    internal fun setShowingFullScreen(showing: Boolean) {
        showingFullScreen = showing
    }

    fun load(activity: Activity, adUnitId: String, callback: AdCallback? = null) {
        AdsSdk.consent.initializeMobileAds(activity.applicationContext)
        AppOpenAd.load(
            activity,
            adUnitId,
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    loaded = ad
                    loadedAt = System.currentTimeMillis()
                    ad.setOnPaidEventListener { value ->
                        AdsSdk.paid(
                            PaidEventMapper.map(value, ad.responseInfo, "app_open", adUnitId),
                        )
                    }
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
        val ad = loaded.takeIf { isFresh() }
        if (ad == null) {
            callback.onAdFailedToLoad(AdError(message = "app open not ready"))
            callback.safeNext()
            return
        }
        present(activity, ad, callback)
    }

    internal fun onProcessStart(activity: Activity?) {
        if (activity == null) return
        if (!globallyEnabled) return
        if (!AdsSdk.config.enableResumeAds) return
        val resumeKey = AdsSdk.config.resumeRemoteKey
        val resumeEnabled = if (resumeKey.isNullOrBlank()) {
            AdsSdk.config.enableResumeAds
        } else {
            AdsSdk.remote.getBoolean(resumeKey, AdsSdk.config.enableResumeAds)
        }
        if (!resumeEnabled) {
            return
        }
        if (excluded.any { it.isInstance(activity) }) {
            SdkLog.d("Resume ads skipped: excluded ${activity.javaClass.simpleName}")
            return
        }
        if (showingFullScreen || showing) return

        when (AdsSdk.config.resumeFormat) {
            ResumeFormat.AppOpen -> showResumeAppOpen(activity)
            ResumeFormat.Interstitial -> showResumeInter(activity)
        }
    }

    private fun showResumeAppOpen(activity: Activity) {
        val unit = AdsSdk.config.resumeAdUnitId ?: return
        val cb = object : AdCallback {
            override fun onNextAction() = Unit
        }
        val ad = loaded.takeIf { isFresh() }
        if (ad != null) {
            present(activity, ad, cb)
        } else {
            load(activity, unit, object : AdCallback {
                override fun onNextAction() = Unit
                override fun onAdLoaded() {
                    loaded?.let { present(activity, it, cb) }
                }
            })
        }
    }

    private fun showResumeInter(activity: Activity) {
        val unit = AdsSdk.config.resumeAdUnitId ?: AdsSdk.config.openAds.interstitialAdUnitId ?: return
        AdsSdk.interstitial.loadAndShow(
            activity,
            unit,
            ignoreInterval = true,
            callback = object : AdCallback {
                override fun onNextAction() = Unit
            },
            reason = InterShowReason.Resume,
        )
    }

    private fun isFresh(): Boolean {
        return loaded != null && System.currentTimeMillis() - loadedAt < FOUR_HOURS_MS
    }

    private fun present(activity: Activity, ad: AppOpenAd, callback: AdCallback) {
        if (showing) {
            callback.safeNext()
            return
        }
        showing = true
        showingFullScreen = true
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() = callback.onAdShown()
            override fun onAdImpression() = callback.onAdImpression()
            override fun onAdClicked() = callback.onAdClicked()
            override fun onAdDismissedFullScreenContent() {
                showing = false
                showingFullScreen = false
                loaded = null
                callback.onAdDismissed()
                callback.safeNext()
            }

            override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                showing = false
                showingFullScreen = false
                loaded = null
                callback.onAdFailedToLoad(AdError(error.code, error.message))
                callback.safeNext()
            }
        }
        ad.show(activity)
    }

    private companion object {
        const val FOUR_HOURS_MS = 4 * 60 * 60 * 1000L
    }
}
