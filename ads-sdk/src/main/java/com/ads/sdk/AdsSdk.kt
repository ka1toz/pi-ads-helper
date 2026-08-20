package com.ads.sdk

import android.app.Activity
import android.app.Application
import android.content.Context
import com.ads.sdk.appopen.AppOpenAds
import com.ads.sdk.banner.BannerAds
import com.ads.sdk.bottom.BottomAds
import com.ads.sdk.consent.ConsentController
import com.ads.sdk.interstitial.InterstitialAds
import com.ads.sdk.internal.IntervalGate
import com.ads.sdk.internal.NetworkUtils
import com.ads.sdk.internal.SdkLog
import com.ads.sdk.lifecycle.SdkLifecycle
import com.ads.sdk.nativead.NativeAds
import com.ads.sdk.openads.OpenAdsController
import com.ads.sdk.remote.AdsRemoteConfig
import com.ads.sdk.rewarded.RewardedAds
import com.ads.sdk.splash.SplashAds
import android.os.SystemClock

object AdsSdk {
    @Volatile
    private var initialized = false

    @Volatile
    private var _config: AdsConfig? = null

    @Volatile
    private var _remote: AdsRemoteConfig? = null

    @Volatile
    private var lifecycle: SdkLifecycle? = null

    internal val interval = IntervalGate(
        intervalMs = { (remote.interstitialIntervalSec().toLong()) * 1000L },
        nowMs = { SystemClock.elapsedRealtime() },
    )

    val consent = ConsentController()
    val interstitial = InterstitialAds()
    val splash = SplashAds()
    val banner = BannerAds()
    val native = NativeAds()
    val appOpen = AppOpenAds()
    val rewarded = RewardedAds()
    val openAds = OpenAdsController()
    val bottom = BottomAds()

    val config: AdsConfig
        get() = _config ?: error("AdsSdk.init() must be called first")

    internal val configOrNull: AdsConfig? get() = _config

    val remoteConfig: AdsRemoteConfig
        get() = _remote ?: error("AdsSdk.init() must be called first")

    internal val remote: AdsRemoteConfig get() = remoteConfig

    fun init(application: Application, config: AdsConfig, onReady: (() -> Unit)? = null) {
        _config = config
        _remote = AdsRemoteConfig(config)
        if (!initialized) {
            initialized = true
            lifecycle = SdkLifecycle(application) { activity ->
                appOpen.onProcessStart(activity)
            }
        }
        remote.applyAsync {
            SdkLog.d("AdsSdk ready policy=${config.remote} interval=${remote.interstitialIntervalSec()}s")
            onReady?.invoke()
        }
    }

    fun currentActivity(): Activity? = lifecycle?.currentActivity

    fun isOnline(context: Context): Boolean = NetworkUtils.isOnline(context)

    internal fun funnel(event: FunnelEvent) {
        runCatching { configOrNull?.funnelLogger?.onEvent(event) }
    }

    internal fun paid(event: PaidAdEvent) {
        runCatching { configOrNull?.revenueLogger?.onPaid(event) }
    }
}
