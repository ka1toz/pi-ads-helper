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
)

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
