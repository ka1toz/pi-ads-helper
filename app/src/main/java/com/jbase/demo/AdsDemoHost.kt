package com.jbase.demo

import android.app.Activity
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.lang.reflect.Proxy

class AdsDemoHost {
    private val admobService: Any? = classInstance("com.jb.admobadshelper.AdmobHelper")
    private val maxService: Any? = classInstance("com.jb.maxads.MaxAdsService")
    private val remoteConfig: Any? = classInstance("com.jb.remoteconfig.FirebaseRemoteConfigService")
    private val analytics: Any? = classInstance("com.jb.analytic.AnalyticServices")

    private val _state = MutableStateFlow(AdsUiState())
    val state: StateFlow<AdsUiState> = _state

    private val listener: Any? = createAdsEventListener()

    fun setMode(mode: AdNetworkMode) {
        _state.update { it.copy(mode = mode) }
        log("Mode -> ${mode.label}")
    }

    fun initialize(activity: Activity) {
        _state.update { it.copy(initializing = true, lastError = null) }
        log("Initializing services")

        runCatching {
            analytics.callOptional("Init", activity, emptyArray<String>())
            remoteConfig.callOptional("Init")
            remoteConfig.callOptional("InitWaitAds", activity, Runnable {
                log("Remote config ready")
            }, Runnable {
                log("Remote config fallback/defaults")
            })

            listener?.let {
                admobService.callOptional("SetAdsCallback", it)
                maxService.callOptional("SetEventListener", it)
            } ?: log("AdsEventListener class missing")

            // The original Unity AndroidBridge passed IDs from Android resources/runtime config.
            // Empty values preserve the demo shell; fill local.properties/resources for real ad units.
            val demoArgs = arrayOf("", "", "", "", "", "", "", "", "", "", "")
            admobService.callOptional("Init", activity, demoArgs)
            maxService.callOptional("Init", activity, demoArgs)

            initFormats(activity)
        }.onFailure { error ->
            _state.update { it.copy(lastError = error.message) }
            log("Init failed: ${error.message}")
        }

        _state.update { it.copy(initializing = false) }
        refreshReadiness()
    }

    fun initFormats(activity: Activity) {
        log("Initializing ad formats")
        listOf(admobService, maxService).forEach { service ->
            service.callOptional("InitOpenAppAdUnit", activity)
            service.callOptional("InitMrecAd", activity)
            service.callOptional("InitBannerAd", activity)
            service.callOptional("InitInterAd", activity)
            service.callOptional("InitRewardAd", activity)
        }
        refreshReadiness()
    }

    fun show(activity: Activity, format: AdFormat) {
        val mode = _state.value.mode
        val attempted = showOnNetwork(activity, mode.primary, format, primary = true)
        if (!attempted && mode.fallback != null) {
            showOnNetwork(activity, mode.fallback, format, primary = false)
        } else if (!attempted) {
            log("${format.label}: no ready ad in ${mode.label}")
        }
        refreshReadiness()
    }

    fun hideAll() {
        listOf(admobService, maxService).forEach { service ->
            service.callOptional("HideBanner")
            service.callOptional("HideMREC")
            service.callOptional("HideMrec")
            service.callOptional("HideNativeBanner")
            service.callOptional("HideNativeMREC")
            service.callOptional("HideNativeMrec")
        }
        log("Hide requested for banner/MREC/native")
        refreshReadiness()
    }

    private fun showOnNetwork(activity: Activity, network: AdNetwork, format: AdFormat, primary: Boolean): Boolean {
        val service = when (network) {
            AdNetwork.ADMOB -> admobService
            AdNetwork.MAX -> maxService
        }
        val suffix = if (primary) "primary" else "fallback"
        val ready = isReady(network, format)

        if (!ready && format !in setOf(AdFormat.BANNER, AdFormat.MREC, AdFormat.NATIVE_BANNER, AdFormat.NATIVE_MREC, AdFormat.NATIVE_COLLAPSIBLE)) {
            log("${network.name} ${format.label} not ready ($suffix)")
            return false
        }

        when (format) {
            AdFormat.INTER -> service.callOptional("ShowInter", activity, 0, "demo_inter")
            AdFormat.REWARD -> service.callOptional("ShowReward", activity, 0, "demo_reward")
            AdFormat.BANNER -> service.callOptional("ShowBanner", activity)
            AdFormat.MREC -> service.callOptional("ShowMREC", activity)
            AdFormat.AOA -> service.callOptional("ShowOpenAppAds", activity)
            AdFormat.NATIVE_FULLSCREEN -> service.callOptional("ShowNativeAds", activity)
            AdFormat.NATIVE_BANNER -> service.callOptional("ShowNativeBanner", activity)
            AdFormat.NATIVE_MREC -> service.callOptional("ShowNativeMREC", activity)
            AdFormat.NATIVE_COLLAPSIBLE -> service.callOptional("ShowBanner", activity)
        }

        log("${network.name} ${format.label} show requested ($suffix)")
        return true
    }

    private fun isReady(network: AdNetwork, format: AdFormat): Boolean {
        val service = when (network) {
            AdNetwork.ADMOB -> admobService
            AdNetwork.MAX -> maxService
        }
        return when (format) {
            AdFormat.INTER -> service.callBoolean("IsInterReady")
            AdFormat.REWARD -> service.callBoolean("IsRewardReady")
            AdFormat.BANNER -> service.callBoolean("IsBannerLoaded", default = true)
            AdFormat.MREC -> service.callBoolean("IsMrecAvaiable", default = true)
            AdFormat.AOA -> service.callBoolean("IsOpenAppAdsAvailable")
            AdFormat.NATIVE_FULLSCREEN,
            AdFormat.NATIVE_BANNER,
            AdFormat.NATIVE_MREC,
            AdFormat.NATIVE_COLLAPSIBLE -> service.callBoolean("IsNativeAdsReady", default = true)
        }
    }

    private fun refreshReadiness() {
        _state.update {
            it.copy(
                admobReady = admobService != null,
                maxReady = maxService != null
            )
        }
    }

    private fun log(message: String) {
        val stamp = (SystemClock.elapsedRealtime() / 1000f).let { "%.1f".format(it) }
        _state.update { state ->
            val next = (listOf("[$stamp] $message") + state.logs).take(14)
            state.copy(logs = next)
        }
    }

    private fun createAdsEventListener(): Any? {
        return runCatching {
            val listenerClass = Class.forName("com.jb.ads.AdsEventListener")
            Proxy.newProxyInstance(
                listenerClass.classLoader,
                arrayOf(listenerClass)
            ) { _, method, args ->
                when (method.name) {
                    "onAOAAdHidden" -> log("AOA hidden")
                    "onAOAFailedToLoad" -> log("AOA failed to load")
                    "onAdServiceLoaded" -> log("Ad service loaded")
                    "onVideoRewardLoaded" -> log("Reward loaded")
                    "onVideoRewardDisplayed" -> log("Reward displayed")
                    "onVideoRewardUserRewarded" -> log("Reward earned: ${args?.firstOrNull()?.toString().orEmpty()}")
                    "onInterLoaded" -> log("Inter loaded")
                    "onInterDisplayed" -> log("Inter displayed")
                    "onInterHidden" -> log("Inter hidden")
                    "onBannerDisplayed" -> log("Banner displayed")
                    "onMrecDisplayed" -> log("MREC displayed")
                    "onAOADisplayed" -> log("AOA displayed")
                    "onAdClicked" -> log("Ad clicked")
                    "onAdRevenuePaid" -> log("Revenue: ${args?.joinToString().orEmpty()}")
                    "onSDKInitialized" -> {
                        log("SDK initialized callback")
                        refreshReadiness()
                    }
                    "onBannerLoaded" -> log("Banner loaded height=${args?.firstOrNull()?.toString().orEmpty()}dp")
                    "onBannerDisplayFailed" -> log("Banner display failed")
                    "onBannerCollapDisplay" -> log("Collapsible banner displayed")
                    "onMrecLoaded" -> log("MREC loaded")
                    "onMrecDisplayFailed" -> log("MREC display failed")
                    "onVideoRewardHidden" -> log("Reward hidden")
                    "onVideoRewardDisplayedFailed" -> log("Reward display failed")
                    "onInterDisplayFailed" -> log("Inter display failed")
                    else -> log("Callback ${method.name}: ${args?.joinToString().orEmpty()}")
                }
                null
            }
        }.getOrNull()
    }
}

private fun Any?.callOptional(methodName: String, vararg args: Any?): Any? {
    return this?.callOptional(methodName, *args)
}

private fun Any?.callBoolean(methodName: String, default: Boolean = false, vararg args: Any?): Boolean {
    return this?.callBoolean(methodName, default, *args) ?: default
}
