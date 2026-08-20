package com.ads.sdk.nativead

import android.app.Activity
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import com.ads.sdk.AdsSdk
import com.ads.sdk.NativeTemplate
import com.ads.sdk.R
import com.ads.sdk.callback.AdCallback
import com.ads.sdk.callback.AdError
import com.ads.sdk.internal.MainThread
import com.ads.sdk.internal.SdkLog
import com.ads.sdk.revenue.PaidEventMapper
import com.ads.sdk.TestAdUnits
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.VideoOptions
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import java.util.Collections

class NativeAds internal constructor() {

    private data class Cached(
        val ad: NativeAd,
        val loadedAt: Long = System.currentTimeMillis(),
    )

    private val cache = LinkedHashMap<String, Cached>()

    /** AdLoader is not a static load API — GC before callback drops the native ad silently. */
    private val inFlightLoaders = Collections.synchronizedSet(mutableSetOf<AdLoader>())

    fun preload(context: Context, placement: String, adUnitId: String, callback: AdCallback? = null) {
        loadInternal(context, adUnitId, callback) { ad ->
            cache[placement]?.ad?.destroy()
            cache[placement] = Cached(ad)
        }
    }

    fun load(context: Context, adUnitId: String, callback: NativeLoadCallback) {
        loadInternal(context, adUnitId, callback) { ad ->
            callback.onNativeLoaded(ad)
        }
    }

    fun takePreloaded(placement: String): NativeAd? {
        val cached = cache.remove(placement) ?: return null
        if (System.currentTimeMillis() - cached.loadedAt > TTL_MS) {
            cached.ad.destroy()
            return null
        }
        return cached.ad
    }

    fun bind(nativeAd: NativeAd, adView: NativeAdView) {
        NativeAssetBinder.populate(adView, nativeAd)
        adView.setNativeAd(nativeAd)
    }

    /**
     * Inflate [layoutRes] into [container] and bind [nativeAd].
     *
     * Root may be a [NativeAdView], or a wrapper that contains one
     * (`@+id/nativeAdView` or `@+id/ads_sdk_native_ad_view`) — same pattern as
     * DIY fullscreen / collapsible layouts.
     */
    fun bind(
        activity: Activity,
        @LayoutRes layoutRes: Int,
        container: ViewGroup,
        nativeAd: NativeAd,
    ): NativeAdView {
        unbind(container, destroyAd = true, keepAd = nativeAd)
        val root = LayoutInflater.from(activity).inflate(layoutRes, container, false)
        val adView = NativeAssetBinder.findNativeAdView(root)
            ?: error(
                "Custom native layout must be a NativeAdView or contain one " +
                    "(id nativeAdView / ads_sdk_native_ad_view)",
            )
        bind(nativeAd, adView)
        container.addView(root)
        container.setTag(R.id.ads_sdk_native_view, adView)
        container.setTag(R.id.ads_sdk_native_ad, nativeAd)
        container.visibility = View.VISIBLE
        container.requestLayout()
        return adView
    }

    fun bindTemplate(
        activity: Activity,
        container: ViewGroup,
        nativeAd: NativeAd,
        template: NativeTemplate,
    ): NativeAdView {
        return bind(activity, layoutFor(template), container, nativeAd)
    }

    fun loadAndBind(
        activity: Activity,
        container: ViewGroup,
        adUnitId: String,
        template: NativeTemplate = NativeTemplate.Medium,
        shimmer: View? = null,
        placement: String? = null,
        callback: AdCallback? = null,
    ) {
        loadAndBind(activity, container, adUnitId, layoutFor(template), shimmer, placement, callback)
    }

    fun loadAndBind(
        activity: Activity,
        container: ViewGroup,
        adUnitId: String,
        @LayoutRes layoutRes: Int,
        shimmer: View? = null,
        placement: String? = null,
        callback: AdCallback? = null,
    ) {
        shimmer?.visibility = View.VISIBLE
        val preloaded = placement?.let { takePreloaded(it) }
        if (preloaded != null) {
            shimmer?.visibility = View.GONE
            bind(activity, layoutRes, container, preloaded)
            callback?.onAdLoaded()
            return
        }
        load(activity, adUnitId, object : NativeLoadCallback {
            override fun onNextAction() = Unit
            override fun onNativeLoaded(ad: NativeAd) {
                if (activity.isDestroyed) {
                    ad.destroy()
                    return
                }
                shimmer?.visibility = View.GONE
                bind(activity, layoutRes, container, ad)
                callback?.onAdLoaded()
            }

            override fun onAdFailedToLoad(error: AdError?) {
                shimmer?.visibility = View.GONE
                callback?.onAdFailedToLoad(error)
            }
        })
    }

    fun loadCollapsible(
        activity: Activity,
        container: ViewGroup,
        config: com.ads.sdk.NativeCollapConfig,
        placement: String? = null,
        callback: AdCallback? = null,
    ) {
        NativeCollapsibleController(this).attach(activity, container, config, placement, callback)
    }

    fun destroy(container: ViewGroup) {
        (container.getTag(R.id.ads_sdk_collap_controller) as? NativeCollapsibleController)?.release()
        container.setTag(R.id.ads_sdk_collap_controller, null)
        unbind(container, destroyAd = true, keepAd = null)
        container.removeAllViews()
    }

    internal fun destroyContents(container: ViewGroup) {
        unbind(container, destroyAd = true, keepAd = null)
        container.removeAllViews()
    }

    private fun unbind(container: ViewGroup, destroyAd: Boolean, keepAd: NativeAd?) {
        val ad = container.getTag(R.id.ads_sdk_native_ad) as? NativeAd
        container.setTag(R.id.ads_sdk_native_view, null)
        container.setTag(R.id.ads_sdk_native_ad, null)
        container.removeAllViews()
        if (destroyAd && ad != null && ad !== keepAd) {
            runCatching { ad.destroy() }
        }
    }

    fun destroyPlacement(placement: String) {
        cache.remove(placement)?.ad?.destroy()
    }

    internal fun layoutFor(template: NativeTemplate): Int = when (template) {
        NativeTemplate.Small -> R.layout.ads_native_small
        NativeTemplate.Medium -> R.layout.ads_native_medium
        NativeTemplate.MediumCtaFirst -> R.layout.ads_native_medium_cta_first
        NativeTemplate.Fullscreen -> R.layout.ads_native_fullscreen
    }

    private fun loadInternal(
        context: Context,
        adUnitId: String,
        callback: AdCallback?,
        onLoaded: (NativeAd) -> Unit,
    ) {
        requestNative(context.applicationContext, adUnitId, callback, onLoaded, debugFallbackUnit(adUnitId))
    }

    private fun debugFallbackUnit(adUnitId: String): String? {
        if (AdsSdk.configOrNull?.debug != true) return null
        return when (adUnitId) {
            TestAdUnits.NATIVE -> TestAdUnits.NATIVE_VIDEO
            TestAdUnits.NATIVE_VIDEO -> TestAdUnits.NATIVE
            else -> null
        }
    }

    private fun requestNative(
        app: Context,
        adUnitId: String,
        callback: AdCallback?,
        onLoaded: (NativeAd) -> Unit,
        fallbackAdUnitId: String?,
    ) {
        AdsSdk.consent.initializeMobileAds(app) {
            lateinit var loader: AdLoader
            loader = AdLoader.Builder(app, adUnitId)
                .forNativeAd { ad ->
                    inFlightLoaders.remove(loader)
                    ad.setOnPaidEventListener { value ->
                        AdsSdk.paid(
                            PaidEventMapper.map(value, ad.responseInfo, "native", adUnitId),
                        )
                    }
                    SdkLog.d("Native loaded unit=$adUnitId headline=${ad.headline}")
                    MainThread.post { onLoaded(ad) }
                }
                .withAdListener(object : AdListener() {
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        inFlightLoaders.remove(loader)
                        SdkLog.w("Native failed unit=$adUnitId ${error.code}: ${error.message}")
                        if (fallbackAdUnitId != null && fallbackAdUnitId != adUnitId) {
                            SdkLog.w("Native retry fallback=$fallbackAdUnitId")
                            requestNative(app, fallbackAdUnitId, callback, onLoaded, fallbackAdUnitId = null)
                            return
                        }
                        MainThread.post {
                            callback?.onAdFailedToLoad(AdError(error.code, error.message))
                        }
                    }

                    override fun onAdClicked() {
                        callback?.onAdClicked()
                    }

                    override fun onAdImpression() {
                        callback?.onAdImpression()
                    }
                })
                .withNativeAdOptions(
                    NativeAdOptions.Builder()
                        .setReturnUrlsForImageAssets(false)
                        .setVideoOptions(VideoOptions.Builder().setStartMuted(true).build())
                        .build(),
                )
                .build()
            inFlightLoaders.add(loader)
            loader.loadAd(AdRequest.Builder().build())
        }
    }

    internal fun populate(adView: NativeAdView, nativeAd: NativeAd) {
        NativeAssetBinder.populate(adView, nativeAd)
    }

    interface NativeLoadCallback : AdCallback {
        fun onNativeLoaded(ad: NativeAd)
    }

    private companion object {
        const val TTL_MS = 60 * 60 * 1000L
    }
}
