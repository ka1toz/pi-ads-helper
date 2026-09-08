package com.ads.sdk.mrec

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import com.ads.sdk.AdsSdk
import com.ads.sdk.callback.AdCallback
import com.ads.sdk.callback.AdError
import com.ads.sdk.internal.SdkLog

class MrecAds internal constructor() {

    fun load(
        activity: Activity,
        container: ViewGroup,
        adUnitId: String,
        shimmer: View? = null,
        callback: AdCallback? = null,
    ) {
        if (!AdsSdk.isMax) {
            SdkLog.w("MREC skipped: AdMob session")
            callback?.onAdFailedToLoad(AdError(message = "mrec is MAX-only"))
            return
        }
        if (adUnitId.isBlank()) {
            SdkLog.w("MREC skipped: empty ad unit")
            callback?.onAdFailedToLoad(AdError(message = "mrec ad unit empty"))
            return
        }
        val bridge = AdsSdk.maxBridge
        if (bridge == null) {
            SdkLog.w("MREC skipped: sdk-max not on classpath")
            callback?.onAdFailedToLoad(AdError(message = "sdk-max missing"))
            return
        }
        AdsSdk.ensureNetworkSdk(activity.applicationContext) {
            bridge.loadMrec(activity, container, adUnitId, shimmer, callback)
        }
    }

    fun destroy(container: ViewGroup) {
        AdsSdk.maxBridge?.destroyMrec(container)
    }
}
