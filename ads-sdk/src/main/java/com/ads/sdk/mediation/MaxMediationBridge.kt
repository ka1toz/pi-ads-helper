package com.ads.sdk.mediation

import android.app.Activity
import android.app.Application
import android.view.View
import android.view.ViewGroup
import com.ads.sdk.BannerConfig
import com.ads.sdk.callback.AdCallback

/**
 * Implemented in `:ads-sdk-max`. Loaded by reflection so `:ads-sdk` stays GMA-only
 * unless the app adds `sdk-max`.
 */
interface MaxMediationBridge {
    fun initialize(
        application: Application,
        sdkKey: String,
        testDeviceAdvertisingIds: List<String>,
        onComplete: () -> Unit,
    )

    fun loadBanner(
        activity: Activity,
        container: ViewGroup,
        shimmer: View?,
        config: BannerConfig,
        callback: AdCallback?,
    )

    fun hideBanner(container: ViewGroup)

    fun destroyBanner(container: ViewGroup)

    fun loadMrec(
        activity: Activity,
        container: ViewGroup,
        adUnitId: String,
        shimmer: View?,
        callback: AdCallback?,
    )

    fun destroyMrec(container: ViewGroup)

    fun loadInterstitial(activity: Activity, adUnitId: String, callback: AdCallback?)

    fun isInterstitialReady(): Boolean

    fun showInterstitial(activity: Activity, callback: AdCallback, onClosed: () -> Unit)

    fun loadAppOpen(activity: Activity, adUnitId: String, callback: AdCallback?)

    fun isAppOpenReady(): Boolean

    fun showAppOpen(activity: Activity, callback: AdCallback)
}
