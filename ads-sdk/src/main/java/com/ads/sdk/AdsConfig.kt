package com.ads.sdk

import com.ads.sdk.callback.AdCallback
import com.ads.sdk.callback.AdError

/**
 * Google sample ad units. Safe for debug builds; never ship as production IDs.
 */
object TestAdUnits {
    const val APP_ID = "ca-app-pub-3940256099942544~3347511713"
    const val BANNER = "ca-app-pub-3940256099942544/6300978111"
    const val ADAPTIVE_BANNER = "ca-app-pub-3940256099942544/9214589741"
    const val INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"
    const val REWARDED = "ca-app-pub-3940256099942544/5224354917"
    const val NATIVE = "ca-app-pub-3940256099942544/2247696110"
    const val NATIVE_VIDEO = "ca-app-pub-3940256099942544/1044960115"
    const val APP_OPEN = "ca-app-pub-3940256099942544/9257395921"
}

enum class BannerType {
    Standard,
    Adaptive,
    CollapsibleTop,
    CollapsibleBottom,
    Large,
}

enum class NativeTemplate {
    Small,
    Medium,
    MediumCtaFirst,
    Fullscreen,
}

enum class ResumeFormat {
    AppOpen,
    Interstitial,
}

enum class Mediation {
    AdMob,
    Max,
    ;

    companion object {
        /** Firebase `mediation_type`: 0 = MAX, anything else (default 1) = AdMob. */
        fun fromRemote(value: Int): Mediation = if (value == 0) Max else AdMob
    }
}

enum class OpenAdFormat {
    Off,
    Inter,
    AppOpen,
    ;

    companion object {
        fun parse(raw: String?, default: OpenAdFormat = Off): OpenAdFormat {
            val value = raw?.trim()?.lowercase().orEmpty()
            if (value.isEmpty()) return Off
            return when (value) {
                "inter", "interstitial" -> Inter
                "aoa", "appopen", "app_open", "open" -> AppOpen
                "false", "0", "off", "none" -> Off
                "true", "1" -> if (default == Off) Inter else default
                else -> default
            }
        }
    }
}

data class AdmobAdUnits(
    val banner: String = "",
    val native: String = "",
    val interstitial: String = "",
    val rewarded: String = "",
    val appOpen: String = "",
)

/** MAX catalog — only these four keys. No native / rewarded. */
data class MaxAdUnits(
    val interstitial: String = "",
    val appOpen: String = "",
    val banner: String = "",
    val mrec: String = "",
)

data class ResolvedAdUnits(
    val interstitial: String = "",
    val appOpen: String = "",
    val banner: String = "",
    val mrec: String = "",
    val native: String = "",
    val rewarded: String = "",
) {
    companion object {
        fun resolve(config: AdsConfig, mediation: Mediation): ResolvedAdUnits {
            if (mediation == Mediation.Max) {
                val max = config.max
                return ResolvedAdUnits(
                    interstitial = max?.interstitial.orEmpty(),
                    appOpen = max?.appOpen.orEmpty(),
                    banner = max?.banner.orEmpty(),
                    mrec = max?.mrec.orEmpty(),
                )
            }
            val admob = config.admob
            return ResolvedAdUnits(
                interstitial = admob?.interstitial?.takeIf { it.isNotBlank() }
                    ?: config.openAds.interstitialAdUnitId.orEmpty(),
                appOpen = admob?.appOpen?.takeIf { it.isNotBlank() }
                    ?: config.openAds.appOpenAdUnitId.orEmpty(),
                banner = admob?.banner.orEmpty(),
                native = admob?.native.orEmpty(),
                rewarded = admob?.rewarded.orEmpty(),
            )
        }
    }
}

enum class RemoteConfigPolicy {
    /** Do not touch Firebase; use numeric/string fields on [AdsConfig]. */
    None,
    /** Read already-activated FirebaseRemoteConfig. App owns fetch. */
    ReadOnly,
    /** SDK setDefaults + fetchAndActivate, then read. */
    FetchAndRead,
}

enum class InterShowReason {
    Content,
    Open,
    Resume,
}

data class AdsConfig(
    val debug: Boolean = false,
    /**
     * Host build flag. Pass a Gradle/BuildConfig boolean: true on dev, false on product.
     * [AdsSdk.openAdInspector] opens only when this is true.
     */
    val isDebuggableAds: Boolean = false,
    /** When true, UMP debug geography is EEA. Default false — EEA + old emulator GMS often breaks native test ads. */
    val debugConsentEea: Boolean = false,
    val testDeviceIds: List<String> = emptyList(),
    val appsFlyerDevKey: String? = null,
    val adjustToken: String? = null,
    val interstitialIntervalSec: Int = 15,
    val interstitialIntervalRemoteKey: String? = "interval_show_interstitial",
    val enableResumeAds: Boolean = true,
    val resumeRemoteKey: String? = "show_resum_ads",
    val resumeAdUnitId: String? = TestAdUnits.APP_OPEN,
    val resumeFormat: ResumeFormat = ResumeFormat.AppOpen,
    val openAds: OpenAdsConfig = OpenAdsConfig(),
    val remote: RemoteConfigPolicy = RemoteConfigPolicy.None,
    val revenueLogger: RevenueLogger? = null,
    val funnelLogger: FunnelLogger? = null,
    val admob: AdmobAdUnits? = null,
    val max: MaxAdUnits? = null,
    val applovinSdkKey: String? = null,
    /** Firebase number: 0 = MAX, 1 = AdMob. Default 1. */
    val mediationRemoteKey: String? = "mediation_type",
    val mediationDefault: Int = 1,
    /** Pirago `resume_type` string (`inter` / `aoa` / empty). Null = use [resumeRemoteKey] boolean. */
    val resumeTypeRemoteKey: String? = null,
    val resumeTypeDefault: OpenAdFormat = OpenAdFormat.Inter,
    val resumeAdsIntervalSec: Int = 15,
    val resumeAdsIntervalRemoteKey: String? = "resume_ads_interval",
)

data class OpenAdsConfig(
    val enabledRemoteKey: String? = "show_open_ads",
    val firstOpenRemoteKey: String? = "show_open_ads_first_open",
    val typeIsInterRemoteKey: String? = "show_opens_ads_type",
    val appOpenAdUnitId: String? = TestAdUnits.APP_OPEN,
    val interstitialAdUnitId: String? = TestAdUnits.INTERSTITIAL,
    val enabledDefault: Boolean = true,
    val firstOpenDefault: Boolean = true,
    val typeIsInterDefault: Boolean = false,
    /** Pirago `aoa_type` (`inter` / `aoa` / empty). Null = boolean [enabledRemoteKey] + [typeIsInterRemoteKey]. */
    val formatRemoteKey: String? = null,
    val formatDefault: OpenAdFormat = OpenAdFormat.AppOpen,
)

data class BannerConfig(
    val adUnitId: String,
    val type: BannerType = BannerType.Adaptive,
    val refreshSec: Int = 0,
    val collapsibleFetchIntervalSec: Int = 0,
)

data class NativeCollapConfig(
    val expandedUnitId: String,
    val collapsedUnitId: String = expandedUnitId,
    val reloadSecRemoteKey: String? = "time_reload_collap_ad",
    val reloadSec: Int = 15,
    /** DIY `is_show_native_bottom`: collapsed fill is Native Small. False → [collapsedBanner]. */
    val collapsedNativeSmall: Boolean = true,
    val collapsedBanner: BannerConfig? = null,
    /**
     * App-owned expanded native XML. `0` = SDK [NativeTemplate.Medium].
     * Chrome still uses SDK `ads_native_collapsible` (expanded/collapsed slots + collapse button).
     */
    @androidx.annotation.LayoutRes val expandedLayoutRes: Int = 0,
    /**
     * App-owned collapsed native XML when [collapsedNativeSmall] is true.
     * `0` = SDK [NativeTemplate.Small].
     */
    @androidx.annotation.LayoutRes val collapsedLayoutRes: Int = 0,
)

data class BottomAdConfig(
    /** DIY `is_show_native_bottom`. */
    val showNativeSmall: Boolean,
    val nativeAdUnitId: String,
    val banner: BannerConfig,
    /**
     * DIY Home slot: start as native collapsible, collapse to Small or Banner.
     * When false, this is a static Native Small **or** Banner (no expand/collapse).
     */
    val collapsible: Boolean = false,
    val reloadSec: Int = 0,
    val expandedUnitId: String = nativeAdUnitId,
    /** Forwarded to [NativeCollapConfig.expandedLayoutRes] when collapsible. */
    @androidx.annotation.LayoutRes val expandedLayoutRes: Int = 0,
    /** Forwarded to [NativeCollapConfig.collapsedLayoutRes] when collapsible. */
    @androidx.annotation.LayoutRes val collapsedLayoutRes: Int = 0,
    /**
     * Static Native Small path only (`collapsible=false`, `showNativeSmall=true`).
     * `0` = SDK [NativeTemplate.Small].
     */
    @androidx.annotation.LayoutRes val nativeLayoutRes: Int = 0,
)

fun interface RevenueLogger {
    fun onPaid(event: PaidAdEvent)
}

fun interface FunnelLogger {
    fun onEvent(event: FunnelEvent)
}

enum class FunnelEvent(val key: String) {
    InterEligible("af_inters_ad_eligible"),
    InterApiCalled("af_inters_api_called"),
    InterDisplayed("af_inters_displayed"),
    RewardedEligible("af_rewarded_ad_eligible"),
    RewardedApiCalled("af_rewarded_api_called"),
    RewardedDisplayed("af_rewarded_ad_displayed"),
}

data class PaidAdEvent(
    val valueMicros: Long,
    val currencyCode: String,
    val precision: Int,
    val adPlatform: String = "AdMob",
    val adSource: String?,
    val adFormat: String,
    val adUnitId: String,
) {
    companion object {
        fun fromMax(
            revenueUsd: Double,
            networkName: String?,
            adFormat: String,
            adUnitId: String,
        ): PaidAdEvent {
            return PaidAdEvent(
                valueMicros = (revenueUsd * 1_000_000.0).toLong(),
                currencyCode = "USD",
                precision = 0,
                adPlatform = "AppLovin",
                adSource = networkName,
                adFormat = adFormat,
                adUnitId = adUnitId,
            )
        }
    }
}

open class SimpleAdCallback : AdCallback {
    override fun onNextAction() = Unit
}

typealias NextAction = () -> Unit

fun AdCallback.safeNext() {
    try {
        onNextAction()
    } catch (_: Exception) {
        // Host navigation must not crash the SDK.
    }
}

fun AdError.toThrowable(): Throwable = Throwable("$code: $message")
