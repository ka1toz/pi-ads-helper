package com.ads.sdk.max

import android.app.Activity
import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.ads.sdk.AdsSdk
import com.ads.sdk.BannerConfig
import com.ads.sdk.BannerType
import com.ads.sdk.PaidAdEvent
import com.ads.sdk.R
import com.ads.sdk.callback.AdCallback
import com.ads.sdk.callback.AdError
import com.ads.sdk.mediation.MaxMediationBridge
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxAdFormat
import com.applovin.mediation.MaxAdListener
import com.applovin.mediation.MaxAdRevenueListener
import com.applovin.mediation.MaxAdViewAdListener
import com.applovin.mediation.MaxError
import com.applovin.mediation.ads.MaxAdView
import com.applovin.mediation.ads.MaxAppOpenAd
import com.applovin.mediation.ads.MaxInterstitialAd
import com.applovin.sdk.AppLovinMediationProvider
import com.applovin.sdk.AppLovinSdk
import com.applovin.sdk.AppLovinSdkInitializationConfiguration
import com.applovin.sdk.AppLovinSdkUtils
import kotlin.math.pow

/**
 * Public no-arg constructor — loaded from `:ads-sdk` via reflection.
 */
class MaxMediationBridgeImpl : MaxMediationBridge {

    private val handler = Handler(Looper.getMainLooper())

    @Volatile private var interstitial: MaxInterstitialAd? = null
    @Volatile private var interstitialUnitId: String? = null
    @Volatile private var interstitialCallback: AdCallback? = null
    @Volatile private var interstitialClosed: (() -> Unit)? = null
    private var interstitialRetry = 0

    @Volatile private var appOpen: MaxAppOpenAd? = null
    @Volatile private var appOpenCallback: AdCallback? = null
    private var appOpenRetry = 0

    override fun initialize(
        application: Application,
        sdkKey: String,
        testDeviceAdvertisingIds: List<String>,
        onComplete: () -> Unit,
    ) {
        val builder = AppLovinSdkInitializationConfiguration.builder(sdkKey, application)
            .setMediationProvider(AppLovinMediationProvider.MAX)
        if (testDeviceAdvertisingIds.isNotEmpty()) {
            builder.setTestDeviceAdvertisingIds(testDeviceAdvertisingIds)
        }
        AppLovinSdk.getInstance(application).initialize(builder.build()) {
            Log.d(TAG, "AppLovin MAX initialized")
            onComplete()
        }
    }

    override fun showMediationDebugger(activity: Activity) {
        handler.post {
            AppLovinSdk.getInstance(activity).showMediationDebugger()
        }
    }

    override fun loadBanner(
        activity: Activity,
        container: ViewGroup,
        shimmer: View?,
        config: BannerConfig,
        callback: AdCallback?,
    ) {
        destroyBanner(container)
        shimmer?.visibility = View.VISIBLE
        val adView = MaxAdView(config.adUnitId, activity)
        adView.setExtraParameter("adaptive_banner", "true")
        val heightDp = when (config.type) {
            BannerType.Large -> 90
            BannerType.Standard -> 50
            else -> 50
        }
        val heightPx = AppLovinSdkUtils.dpToPx(activity, heightDp)
        adView.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            heightPx,
        )
        bindViewListener(adView, config.adUnitId, "banner", shimmer, callback)
        container.addView(adView)
        container.setTag(R.id.ads_sdk_max_ad_view, adView)
        adView.loadAd()
    }

    override fun hideBanner(container: ViewGroup) {
        (container.getTag(R.id.ads_sdk_max_ad_view) as? MaxAdView)?.visibility = View.GONE
        container.visibility = View.GONE
    }

    override fun destroyBanner(container: ViewGroup) {
        val adView = container.getTag(R.id.ads_sdk_max_ad_view) as? MaxAdView
        adView?.destroy()
        container.setTag(R.id.ads_sdk_max_ad_view, null)
        if (adView != null) container.removeView(adView)
    }

    override fun loadMrec(
        activity: Activity,
        container: ViewGroup,
        adUnitId: String,
        shimmer: View?,
        callback: AdCallback?,
    ) {
        destroyMrec(container)
        shimmer?.visibility = View.VISIBLE
        val adView = MaxAdView(adUnitId, MaxAdFormat.MREC, activity)
        val widthPx = AppLovinSdkUtils.dpToPx(activity, 300)
        val heightPx = AppLovinSdkUtils.dpToPx(activity, 250)
        adView.layoutParams = FrameLayout.LayoutParams(widthPx, heightPx)
        bindViewListener(adView, adUnitId, "mrec", shimmer, callback)
        container.addView(adView)
        container.setTag(R.id.ads_sdk_mrec_view, adView)
        adView.loadAd()
    }

    override fun destroyMrec(container: ViewGroup) {
        val adView = container.getTag(R.id.ads_sdk_mrec_view) as? MaxAdView
        adView?.destroy()
        container.setTag(R.id.ads_sdk_mrec_view, null)
        if (adView != null) container.removeView(adView)
    }

    override fun loadInterstitial(activity: Activity, adUnitId: String, callback: AdCallback?) {
        interstitialCallback = callback
        val existing = interstitial
        if (existing != null && interstitialUnitId == adUnitId) {
            if (existing.isReady) {
                callback?.onAdLoaded()
                return
            }
            existing.loadAd()
            return
        }
        existing?.setListener(null)
        val ad = MaxInterstitialAd(adUnitId, activity)
        interstitial = ad
        interstitialUnitId = adUnitId
        interstitialRetry = 0
        ad.setRevenueListener { maxAd -> pay(maxAd, "interstitial", adUnitId) }
        ad.setListener(object : MaxAdListener {
            override fun onAdLoaded(maxAd: MaxAd) {
                interstitialRetry = 0
                interstitialCallback?.onAdLoaded()
            }

            override fun onAdLoadFailed(unitId: String, error: MaxError) {
                interstitialCallback?.onAdFailedToLoad(AdError(error.code, error.message))
                retryInterstitial()
            }

            override fun onAdDisplayed(maxAd: MaxAd) {
                interstitialCallback?.onAdShown()
                interstitialCallback?.onAdImpression()
            }

            override fun onAdHidden(maxAd: MaxAd) {
                val closed = interstitialClosed
                interstitialClosed = null
                closed?.invoke()
                ad.loadAd()
            }

            override fun onAdClicked(maxAd: MaxAd) {
                interstitialCallback?.onAdClicked()
            }

            override fun onAdDisplayFailed(maxAd: MaxAd, error: MaxError) {
                interstitialCallback?.onAdFailedToLoad(AdError(error.code, error.message))
                val closed = interstitialClosed
                interstitialClosed = null
                closed?.invoke()
                ad.loadAd()
            }
        })
        ad.loadAd()
    }

    override fun isInterstitialReady(): Boolean = interstitial?.isReady == true

    override fun showInterstitial(activity: Activity, callback: AdCallback, onClosed: () -> Unit) {
        val ad = interstitial
        if (ad == null || !ad.isReady) {
            callback.onAdFailedToLoad(AdError(message = "max interstitial not ready"))
            onClosed()
            return
        }
        interstitialCallback = callback
        interstitialClosed = onClosed
        ad.showAd()
    }

    override fun loadAppOpen(activity: Activity, adUnitId: String, callback: AdCallback?) {
        appOpenCallback = callback
        val existing = appOpen
        if (existing != null) {
            if (existing.isReady) {
                callback?.onAdLoaded()
                return
            }
            existing.loadAd()
            return
        }
        val ad = MaxAppOpenAd(adUnitId, activity)
        appOpen = ad
        appOpenRetry = 0
        ad.setRevenueListener { maxAd -> pay(maxAd, "app_open", adUnitId) }
        ad.setListener(object : MaxAdListener {
            override fun onAdLoaded(maxAd: MaxAd) {
                appOpenRetry = 0
                appOpenCallback?.onAdLoaded()
            }

            override fun onAdLoadFailed(unitId: String, error: MaxError) {
                appOpenCallback?.onAdFailedToLoad(AdError(error.code, error.message))
                retryAppOpen()
            }

            override fun onAdDisplayed(maxAd: MaxAd) {
                appOpenCallback?.onAdShown()
                appOpenCallback?.onAdImpression()
            }

            override fun onAdHidden(maxAd: MaxAd) {
                val cb = appOpenCallback
                appOpenCallback = null
                cb?.onAdDismissed()
                cb?.onNextAction()
                ad.loadAd()
            }

            override fun onAdClicked(maxAd: MaxAd) {
                appOpenCallback?.onAdClicked()
            }

            override fun onAdDisplayFailed(maxAd: MaxAd, error: MaxError) {
                val cb = appOpenCallback
                appOpenCallback = null
                cb?.onAdFailedToLoad(AdError(error.code, error.message))
                cb?.onNextAction()
                ad.loadAd()
            }
        })
        ad.loadAd()
    }

    override fun isAppOpenReady(): Boolean = appOpen?.isReady == true

    override fun showAppOpen(activity: Activity, callback: AdCallback) {
        val ad = appOpen
        if (ad == null || !ad.isReady) {
            callback.onAdFailedToLoad(AdError(message = "max app open not ready"))
            callback.onNextAction()
            return
        }
        appOpenCallback = callback
        ad.showAd()
    }

    private fun bindViewListener(
        adView: MaxAdView,
        adUnitId: String,
        format: String,
        shimmer: View?,
        callback: AdCallback?,
    ) {
        adView.setRevenueListener { maxAd -> pay(maxAd, format, adUnitId) }
        adView.setListener(object : MaxAdViewAdListener {
            override fun onAdLoaded(maxAd: MaxAd) {
                shimmer?.visibility = View.GONE
                callback?.onAdLoaded()
            }

            override fun onAdLoadFailed(unitId: String, error: MaxError) {
                shimmer?.visibility = View.GONE
                callback?.onAdFailedToLoad(AdError(error.code, error.message))
            }

            override fun onAdDisplayed(maxAd: MaxAd) {
                callback?.onAdShown()
                callback?.onAdImpression()
            }

            override fun onAdHidden(maxAd: MaxAd) = Unit
            override fun onAdClicked(maxAd: MaxAd) {
                callback?.onAdClicked()
            }

            override fun onAdDisplayFailed(maxAd: MaxAd, error: MaxError) {
                callback?.onAdFailedToLoad(AdError(error.code, error.message))
            }

            override fun onAdExpanded(maxAd: MaxAd) = Unit
            override fun onAdCollapsed(maxAd: MaxAd) = Unit
        })
    }

    private fun retryInterstitial() {
        val delay = retryDelayMs(interstitialRetry++)
        handler.postDelayed({ interstitial?.loadAd() }, delay)
    }

    private fun retryAppOpen() {
        val delay = retryDelayMs(appOpenRetry++)
        handler.postDelayed({ appOpen?.loadAd() }, delay)
    }

    private fun retryDelayMs(attempt: Int): Long {
        val capped = attempt.coerceAtMost(6)
        return (2.0.pow(capped) * 1000L).toLong().coerceAtMost(64_000L)
    }

    private fun pay(ad: MaxAd, format: String, fallbackUnit: String) {
        AdsSdk.notifyPaid(
            PaidAdEvent.fromMax(
                revenueUsd = ad.revenue,
                networkName = ad.networkName,
                adFormat = format,
                adUnitId = ad.adUnitId.ifBlank { fallbackUnit },
            ),
        )
    }

    private companion object {
        const val TAG = "AdsSdk.MAX"
    }
}
