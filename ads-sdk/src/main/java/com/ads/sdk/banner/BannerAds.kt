package com.ads.sdk.banner

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.ads.sdk.AdsSdk
import com.ads.sdk.BannerConfig
import com.ads.sdk.BannerType
import com.ads.sdk.R
import com.ads.sdk.callback.AdCallback
import com.ads.sdk.callback.AdError
import com.ads.sdk.revenue.PaidEventMapper
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

class BannerAds internal constructor() {

    private val handler = Handler(Looper.getMainLooper())

    fun load(
        activity: Activity,
        container: ViewGroup,
        shimmer: View? = null,
        config: BannerConfig,
        callback: AdCallback? = null,
    ) {
        destroy(container)
        AdsSdk.consent.initializeMobileAds(activity.applicationContext)
        shimmer?.visibility = View.VISIBLE
        val adView = AdView(activity)
        adView.adUnitId = config.adUnitId
        adView.setAdSize(resolveSize(activity, container, config.type))
        container.addView(
            adView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        container.setTag(R.id.ads_sdk_banner_view, adView)
        adView.setOnPaidEventListener { value ->
            AdsSdk.paid(
                PaidEventMapper.map(value, adView.responseInfo, "banner", config.adUnitId),
            )
        }
        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                shimmer?.visibility = View.GONE
                callback?.onAdLoaded()
                scheduleRefresh(activity, container, shimmer, config)
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                shimmer?.visibility = View.GONE
                callback?.onAdFailedToLoad(AdError(error.code, error.message))
            }

            override fun onAdClicked() = callback?.onAdClicked() ?: Unit
            override fun onAdImpression() = callback?.onAdImpression() ?: Unit
            override fun onAdOpened() = callback?.onAdShown() ?: Unit
        }
        adView.loadAd(buildRequest(config))
    }

    fun hide(container: ViewGroup) {
        (container.getTag(R.id.ads_sdk_banner_view) as? AdView)?.visibility = View.GONE
        container.visibility = View.GONE
    }

    fun destroy(container: ViewGroup) {
        cancelRefresh(container)
        val adView = container.getTag(R.id.ads_sdk_banner_view) as? AdView
        adView?.destroy()
        container.setTag(R.id.ads_sdk_banner_view, null)
        if (adView != null) container.removeView(adView)
    }

    private fun scheduleRefresh(
        activity: Activity,
        container: ViewGroup,
        shimmer: View?,
        config: BannerConfig,
    ) {
        val sec = when (config.type) {
            BannerType.CollapsibleTop, BannerType.CollapsibleBottom ->
                config.collapsibleFetchIntervalSec.takeIf { it > 0 } ?: config.refreshSec
            else -> config.refreshSec
        }
        if (sec <= 0) return
        val token = Runnable {
            if (activity.isDestroyed || activity.isFinishing) return@Runnable
            load(activity, container, shimmer, config)
        }
        container.setTag(R.id.ads_sdk_banner_refresh, token)
        handler.postDelayed(token, sec * 1000L)
    }

    private fun cancelRefresh(container: ViewGroup) {
        val token = container.getTag(R.id.ads_sdk_banner_refresh) as? Runnable
        if (token != null) handler.removeCallbacks(token)
        container.setTag(R.id.ads_sdk_banner_refresh, null)
    }

    private fun buildRequest(config: BannerConfig): AdRequest {
        val builder = AdRequest.Builder()
        val collapsible = when (config.type) {
            BannerType.CollapsibleTop -> "top"
            BannerType.CollapsibleBottom -> "bottom"
            else -> null
        }
        if (collapsible != null) {
            val extras = Bundle()
            extras.putString("collapsible", collapsible)
            builder.addNetworkExtrasBundle(AdMobAdapter::class.java, extras)
        }
        return builder.build()
    }

    private fun resolveSize(activity: Activity, container: ViewGroup, type: BannerType): AdSize {
        return when (type) {
            BannerType.Standard -> AdSize.BANNER
            BannerType.Large -> AdSize.LARGE_BANNER
            BannerType.Adaptive,
            BannerType.CollapsibleTop,
            BannerType.CollapsibleBottom,
            -> {
                val widthPx = container.width.takeIf { it > 0 }
                    ?: activity.resources.displayMetrics.widthPixels
                val density = activity.resources.displayMetrics.density
                val widthDp = (widthPx / density).toInt().coerceAtLeast(320)
                AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, widthDp)
            }
        }
    }
}
