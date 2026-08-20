package com.ads.sdk.bottom

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import com.ads.sdk.AdsSdk
import com.ads.sdk.BottomAdConfig
import com.ads.sdk.NativeCollapConfig
import com.ads.sdk.NativeTemplate
import com.ads.sdk.callback.AdCallback
import com.ads.sdk.nativead.NativeCollapPolicy

class BottomAds internal constructor() {

    /**
     * DIY Home bottom slot:
     * - `collapsible=false`: Native Small **or** Banner (static).
     * - `collapsible=true` and (`showNativeSmall` or `reloadSec>0`): native
     *   collapsible that collapses to Small or Banner, then optionally re-expands.
     * - otherwise: plain Banner.
     */
    fun load(
        activity: Activity,
        container: ViewGroup,
        config: BottomAdConfig,
        shimmer: View? = null,
        placement: String? = null,
        callback: AdCallback? = null,
    ) {
        AdsSdk.banner.destroy(container)
        AdsSdk.native.destroy(container)
        val useCollapsible = config.collapsible &&
            NativeCollapPolicy.shouldUseCollapsible(config.showNativeSmall, config.reloadSec)
        if (useCollapsible) {
            AdsSdk.native.loadCollapsible(
                activity,
                container,
                NativeCollapConfig(
                    expandedUnitId = config.expandedUnitId,
                    collapsedUnitId = config.nativeAdUnitId,
                    reloadSecRemoteKey = null,
                    reloadSec = config.reloadSec,
                    collapsedNativeSmall = config.showNativeSmall,
                    collapsedBanner = config.banner,
                ),
                placement = placement,
                callback = callback,
            )
            return
        }
        if (config.showNativeSmall) {
            AdsSdk.native.loadAndBind(
                activity,
                container,
                config.nativeAdUnitId,
                NativeTemplate.Small,
                shimmer,
                callback = callback,
            )
        } else {
            AdsSdk.banner.load(activity, container, shimmer, config.banner, callback)
        }
    }
}
