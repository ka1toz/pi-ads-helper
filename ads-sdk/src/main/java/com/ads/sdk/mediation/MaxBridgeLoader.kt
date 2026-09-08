package com.ads.sdk.mediation

import com.ads.sdk.internal.SdkLog

internal object MaxBridgeLoader {
    private const val IMPL = "com.ads.sdk.max.MaxMediationBridgeImpl"

    fun load(): MaxMediationBridge? {
        return runCatching {
            Class.forName(IMPL)
                .getDeclaredConstructor()
                .newInstance() as MaxMediationBridge
        }.onFailure { error ->
            SdkLog.w("MAX backend missing ($IMPL). Add sdk-max to the app.", error)
        }.getOrNull()
    }
}
