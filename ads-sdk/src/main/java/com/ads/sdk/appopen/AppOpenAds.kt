package com.ads.sdk.appopen

import android.app.Activity
import com.ads.sdk.AdsSdk
import com.ads.sdk.InterShowReason
import com.ads.sdk.OpenAdFormat
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
        if (AdsSdk.isMax) {
            loadMax(activity, adUnitId, callback)
            return
        }
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
        if (AdsSdk.isMax) {
            presentMax(activity, callback, markResume = false)
            return
        }
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
        if (excluded.any { it.isInstance(activity) }) {
            SdkLog.d("Resume ads skipped: excluded ${activity.javaClass.simpleName}")
            return
        }
        if (showingFullScreen || showing) return
        if (!AdsSdk.resumeInterval.canShow(ignore = false)) {
            SdkLog.d("Resume ads skipped: resume interval ${AdsSdk.resumeInterval.remainingMs()}ms left")
            return
        }

        when (resumeFormat()) {
            OpenAdFormat.Off -> return
            OpenAdFormat.AppOpen -> showResumeAppOpen(activity)
            OpenAdFormat.Inter -> showResumeInter(activity)
        }
    }

    private fun resumeFormat(): OpenAdFormat {
        val cfg = AdsSdk.config
        val typeKey = cfg.resumeTypeRemoteKey
        if (!typeKey.isNullOrBlank()) {
            return AdsSdk.remote.openAdFormat(typeKey, cfg.resumeTypeDefault)
        }
        val resumeKey = cfg.resumeRemoteKey
        val resumeEnabled = if (resumeKey.isNullOrBlank()) {
            cfg.enableResumeAds
        } else {
            AdsSdk.remote.getBoolean(resumeKey, cfg.enableResumeAds)
        }
        if (!resumeEnabled) return OpenAdFormat.Off
        return when (cfg.resumeFormat) {
            ResumeFormat.AppOpen -> OpenAdFormat.AppOpen
            ResumeFormat.Interstitial -> OpenAdFormat.Inter
        }
    }

    private fun showResumeAppOpen(activity: Activity) {
        val unit = AdsSdk.units.appOpen.ifBlank { AdsSdk.config.resumeAdUnitId.orEmpty() }
        if (unit.isBlank()) return
        val cb = object : AdCallback {
            override fun onNextAction() = Unit
        }
        if (AdsSdk.isMax) {
            if (AdsSdk.maxBridge?.isAppOpenReady() == true) {
                presentMax(activity, cb, markResume = true)
            } else {
                load(activity, unit, object : AdCallback {
                    override fun onNextAction() = Unit
                    override fun onAdLoaded() = presentMax(activity, cb, markResume = true)
                })
            }
            return
        }
        val ad = loaded.takeIf { isFresh() }
        if (ad != null) {
            present(activity, ad, cb, markResume = true)
        } else {
            load(activity, unit, object : AdCallback {
                override fun onNextAction() = Unit
                override fun onAdLoaded() {
                    loaded?.let { present(activity, it, cb, markResume = true) }
                }
            })
        }
    }

    private fun showResumeInter(activity: Activity) {
        val unit = AdsSdk.units.interstitial.ifBlank {
            AdsSdk.config.resumeAdUnitId ?: AdsSdk.config.openAds.interstitialAdUnitId.orEmpty()
        }
        if (unit.isBlank()) return
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

    private fun present(
        activity: Activity,
        ad: AppOpenAd,
        callback: AdCallback,
        markResume: Boolean = false,
    ) {
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
                if (markResume) AdsSdk.resumeInterval.markContentDismissed()
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

    private fun loadMax(activity: Activity, adUnitId: String, callback: AdCallback?) {
        if (adUnitId.isBlank()) {
            callback?.onAdFailedToLoad(AdError(message = "max app open unit empty"))
            return
        }
        val bridge = AdsSdk.maxBridge
        if (bridge == null) {
            callback?.onAdFailedToLoad(AdError(message = "sdk-max missing"))
            return
        }
        AdsSdk.ensureNetworkSdk(activity.applicationContext) {
            bridge.loadAppOpen(activity, adUnitId, callback)
        }
    }

    private fun presentMax(activity: Activity, callback: AdCallback, markResume: Boolean) {
        val bridge = AdsSdk.maxBridge
        if (bridge == null || !bridge.isAppOpenReady()) {
            callback.onAdFailedToLoad(AdError(message = "max app open not ready"))
            callback.safeNext()
            return
        }
        if (showing) {
            callback.safeNext()
            return
        }
        showing = true
        showingFullScreen = true
        val wrapped = object : AdCallback by callback {
            override fun onAdDismissed() {
                showing = false
                showingFullScreen = false
                if (markResume) AdsSdk.resumeInterval.markContentDismissed()
                callback.onAdDismissed()
            }
            override fun onAdFailedToLoad(error: AdError?) {
                showing = false
                showingFullScreen = false
                callback.onAdFailedToLoad(error)
            }
            override fun onNextAction() {
                showing = false
                showingFullScreen = false
                callback.safeNext()
            }
        }
        bridge.showAppOpen(activity, wrapped)
    }

    private companion object {
        const val FOUR_HOURS_MS = 4 * 60 * 60 * 1000L
    }
}
