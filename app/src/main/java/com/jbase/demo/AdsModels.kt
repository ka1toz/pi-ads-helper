package com.jbase.demo

enum class AdNetworkMode(val label: String) {
    AdmobOnly("AdMob only"),
    MaxOnly("MAX only"),
    AdmobPrimary("AdMob + MAX"),
    MaxPrimary("MAX + AdMob");

    val primary: AdNetwork
        get() = when (this) {
            AdmobOnly, AdmobPrimary -> AdNetwork.ADMOB
            MaxOnly, MaxPrimary -> AdNetwork.MAX
        }

    val fallback: AdNetwork?
        get() = when (this) {
            AdmobOnly, MaxOnly -> null
            AdmobPrimary -> AdNetwork.MAX
            MaxPrimary -> AdNetwork.ADMOB
        }
}

enum class AdNetwork { ADMOB, MAX }

enum class AdFormat(val label: String) {
    INTER("Inter"),
    REWARD("Reward"),
    BANNER("Banner"),
    MREC("MREC"),
    AOA("AOA"),
    NATIVE_FULLSCREEN("Native fullscreen"),
    NATIVE_BANNER("Native banner"),
    NATIVE_MREC("Native MREC"),
    NATIVE_COLLAPSIBLE("Native collapsible")
}

data class AdsUiState(
    val mode: AdNetworkMode = AdNetworkMode.MaxPrimary,
    val initializing: Boolean = false,
    val admobReady: Boolean = false,
    val maxReady: Boolean = false,
    val lastError: String? = null,
    val logs: List<String> = emptyList()
)
