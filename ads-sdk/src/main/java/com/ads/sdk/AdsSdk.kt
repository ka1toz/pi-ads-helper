package com.ads.sdk

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.SystemClock
import com.ads.sdk.appopen.AppOpenAds
import com.ads.sdk.banner.BannerAds
import com.ads.sdk.bottom.BottomAds
import com.ads.sdk.consent.ConsentController
import com.ads.sdk.interstitial.InterstitialAds
import com.ads.sdk.internal.IntervalGate
import com.ads.sdk.internal.NetworkUtils
import com.ads.sdk.internal.SdkLog
import com.ads.sdk.lifecycle.SdkLifecycle
import com.ads.sdk.mediation.MaxBridgeLoader
import com.ads.sdk.mediation.MaxMediationBridge
import com.ads.sdk.mrec.MrecAds
import com.ads.sdk.nativead.NativeAds
import com.ads.sdk.openads.OpenAdsController
import com.ads.sdk.remote.AdsRemoteConfig
import com.ads.sdk.rewarded.RewardedAds
import com.ads.sdk.splash.SplashAds

object AdsSdk {
    @Volatile
    private var initialized = false

    @Volatile
    private var _config: AdsConfig? = null

    @Volatile
    private var _remote: AdsRemoteConfig? = null

    @Volatile
    private var lifecycle: SdkLifecycle? = null

    @Volatile
    private var application: Application? = null

    @Volatile
    private var _mediation: Mediation = Mediation.AdMob

    @Volatile
    private var _units: ResolvedAdUnits = ResolvedAdUnits()

    @Volatile
    private var _maxBridge: MaxMediationBridge? = null

    @Volatile
    private var maxStarted = false

    @Volatile
    private var maxStarting = false

    private val maxLock = Any()
    private val maxPending = mutableListOf<() -> Unit>()

    internal val interval = IntervalGate(
        intervalMs = { (remote.interstitialIntervalSec().toLong()) * 1000L },
        nowMs = { SystemClock.elapsedRealtime() },
    )

    internal val resumeInterval = IntervalGate(
        intervalMs = { (remote.resumeAdsIntervalSec().toLong()) * 1000L },
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
    val mrec = MrecAds()

    val config: AdsConfig
        get() = _config ?: error("AdsSdk.init() must be called first")

    internal val configOrNull: AdsConfig? get() = _config

    val remoteConfig: AdsRemoteConfig
        get() = _remote ?: error("AdsSdk.init() must be called first")

    internal val remote: AdsRemoteConfig get() = remoteConfig

    val isMax: Boolean get() = _mediation == Mediation.Max

    val mediation: Mediation get() = _mediation

    val units: ResolvedAdUnits get() = _units

    internal val maxBridge: MaxMediationBridge? get() = _maxBridge

    fun init(application: Application, config: AdsConfig, onReady: (() -> Unit)? = null) {
        this.application = application
        _config = config
        _remote = AdsRemoteConfig(config)
        applyMediation(config.mediationDefault)
        if (!initialized) {
            initialized = true
            lifecycle = SdkLifecycle(application) { activity ->
                appOpen.onProcessStart(activity)
            }
        }
        remote.applyAsync {
            applyMediationFromRemote()
            SdkLog.d(
                "AdsSdk ready mediation=$_mediation policy=${config.remote} " +
                    "interval=${remote.interstitialIntervalSec()}s",
            )
            onReady?.invoke()
        }
    }

    fun currentActivity(): Activity? = lifecycle?.currentActivity

    fun isOnline(context: Context): Boolean = NetworkUtils.isOnline(context)

    fun notifyPaid(event: PaidAdEvent) {
        paid(event)
    }

    internal fun funnel(event: FunnelEvent) {
        runCatching { configOrNull?.funnelLogger?.onEvent(event) }
    }

    internal fun paid(event: PaidAdEvent) {
        runCatching { configOrNull?.revenueLogger?.onPaid(event) }
    }

    internal fun ensureNetworkSdk(context: Context, onReady: (() -> Unit)? = null) {
        if (isMax) {
            startMax(context, onReady)
        } else {
            consent.initializeMobileAds(context, onReady)
        }
    }

    internal fun startMax(context: Context, onReady: (() -> Unit)? = null) {
        val app = (context.applicationContext as? Application) ?: application
        val bridge = _maxBridge
        if (app == null || bridge == null) {
            SdkLog.w("MAX init skipped: application or sdk-max missing")
            onReady?.invoke()
            return
        }
        val sdkKey = applovinSdkKey(app)
        if (sdkKey.isBlank()) {
            SdkLog.w("MAX init skipped: empty SDK key")
            onReady?.invoke()
            return
        }
        synchronized(maxLock) {
            if (maxStarted) {
                onReady?.let { it() }
                return
            }
            if (onReady != null) maxPending.add(onReady)
            if (maxStarting) return
            maxStarting = true
        }
        val testIds = configOrNull?.testDeviceIds.orEmpty().filter { it.contains('-') }
        bridge.initialize(app, sdkKey, testIds) {
            val waiters: List<() -> Unit>
            synchronized(maxLock) {
                maxStarted = true
                maxStarting = false
                waiters = maxPending.toList()
                maxPending.clear()
            }
            waiters.forEach { it() }
        }
    }

    private fun applyMediationFromRemote() {
        applyMediation(remote.mediationType().let { type ->
            if (type == Mediation.Max) 0 else 1
        })
    }

    private fun applyMediation(remoteValue: Int) {
        val cfg = _config ?: return
        var next = Mediation.fromRemote(remoteValue)
        if (next == Mediation.Max) {
            val bridge = _maxBridge ?: MaxBridgeLoader.load()
            if (bridge == null) {
                SdkLog.w("mediation_type=0 but sdk-max missing; staying on AdMob")
                next = Mediation.AdMob
            } else {
                _maxBridge = bridge
            }
        } else {
            _maxBridge = null
        }
        _mediation = next
        _units = ResolvedAdUnits.resolve(cfg, next)
    }

    private fun applovinSdkKey(context: Context): String {
        configOrNull?.applovinSdkKey?.takeIf { it.isNotBlank() }?.let { return it }
        return runCatching {
            val flags = PackageManager.GET_META_DATA
            val info: ApplicationInfo = context.packageManager.getApplicationInfo(context.packageName, flags)
            info.metaData?.getString("applovin.sdk.key").orEmpty()
        }.getOrDefault("")
    }
}
