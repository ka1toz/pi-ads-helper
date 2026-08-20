package com.ads.sdk.nativead

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import com.ads.sdk.AdsSdk
import com.ads.sdk.NativeCollapConfig
import com.ads.sdk.NativeTemplate
import com.ads.sdk.R
import com.ads.sdk.callback.AdCallback
import com.ads.sdk.internal.SdkLog
import com.google.android.gms.ads.nativead.NativeAd

internal class NativeCollapsibleController(
    private val nativeAds: NativeAds,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var reloadRunnable: Runnable? = null
    private var expandedAd: NativeAd? = null
    private var collapsedAd: NativeAd? = null
    private var collapsedSlot: FrameLayout? = null
    private var released = false

    fun attach(
        activity: Activity,
        container: ViewGroup,
        config: NativeCollapConfig,
        placement: String?,
        callback: AdCallback?,
    ) {
        nativeAds.destroy(container)
        val root = LayoutInflater.from(activity).inflate(R.layout.ads_native_collapsible, container, false)
        container.removeAllViews()
        container.addView(root)
        container.setTag(R.id.ads_sdk_collap_controller, this)

        val expandedSlot = root.findViewById<FrameLayout>(R.id.ads_sdk_collap_expanded)
        val collapsedSlot = root.findViewById<FrameLayout>(R.id.ads_sdk_collap_collapsed)
        val collapseBtn = root.findViewById<ImageButton>(R.id.ads_sdk_collapse)
        this.collapsedSlot = collapsedSlot

        collapsedSlot.visibility = View.GONE
        collapseBtn.visibility = View.GONE
        val reloadKey = config.reloadSecRemoteKey
        val reloadSec = if (reloadKey.isNullOrBlank()) {
            config.reloadSec
        } else {
            AdsSdk.remote.getLong(reloadKey, config.reloadSec.toLong()).toInt()
        }

        fun bindExpanded(ad: NativeAd) {
            expandedAd?.destroy()
            expandedAd = ad
            nativeAds.bindTemplate(activity, expandedSlot, ad, NativeTemplate.Medium)
            expandedSlot.visibility = View.VISIBLE
            collapseBtn.visibility = View.VISIBLE
            clearCollapsedFill(collapsedSlot)
            collapsedSlot.visibility = View.GONE
            warmCollapsed(activity, config, placement)
        }

        val preloaded = placement?.let { nativeAds.takePreloaded(it) }
        if (preloaded != null) {
            bindExpanded(preloaded)
            callback?.onAdLoaded()
        } else {
            nativeAds.load(activity, config.expandedUnitId, object : NativeAds.NativeLoadCallback {
                override fun onNextAction() = Unit
                override fun onNativeLoaded(ad: NativeAd) {
                    if (released || activity.isDestroyed) {
                        ad.destroy()
                        return
                    }
                    bindExpanded(ad)
                    callback?.onAdLoaded()
                }

                override fun onAdFailedToLoad(error: com.ads.sdk.callback.AdError?) {
                    callback?.onAdFailedToLoad(error)
                }
            })
        }

        collapseBtn.setOnClickListener {
            expandedSlot.visibility = View.GONE
            collapseBtn.visibility = View.GONE
            collapsedSlot.visibility = View.VISIBLE
            bindCollapsed(activity, config, placement, collapsedSlot)
            if (NativeCollapPolicy.shouldAutoReexpand(reloadSec)) {
                reloadRunnable?.let { handler.removeCallbacks(it) }
                reloadRunnable = Runnable {
                    if (released || activity.isDestroyed) return@Runnable
                    SdkLog.d("Native collap re-expand after ${reloadSec}s")
                    nativeAds.load(activity, config.expandedUnitId, object : NativeAds.NativeLoadCallback {
                        override fun onNextAction() = Unit
                        override fun onNativeLoaded(ad: NativeAd) {
                            if (released) {
                                ad.destroy()
                                return
                            }
                            bindExpanded(ad)
                        }
                    })
                }
                handler.postDelayed(reloadRunnable!!, NativeCollapPolicy.reloadDelayMs(reloadSec))
            }
        }
    }

    fun release() {
        released = true
        reloadRunnable?.let { handler.removeCallbacks(it) }
        reloadRunnable = null
        collapsedSlot?.let { slot ->
            AdsSdk.banner.destroy(slot)
            nativeAds.destroyContents(slot)
        }
        collapsedSlot = null
        expandedAd?.destroy()
        expandedAd = null
        collapsedAd = null
    }

    private fun clearCollapsedFill(collapsedSlot: FrameLayout) {
        AdsSdk.banner.destroy(collapsedSlot)
        nativeAds.destroyContents(collapsedSlot)
        collapsedAd = null
    }

    private fun warmCollapsed(
        activity: Activity,
        config: NativeCollapConfig,
        placement: String?,
    ) {
        if (!config.collapsedNativeSmall) return
        val key = collapsedPlacement(placement) ?: return
        nativeAds.preload(activity.applicationContext, key, config.collapsedUnitId)
    }

    private fun bindCollapsed(
        activity: Activity,
        config: NativeCollapConfig,
        placement: String?,
        collapsedSlot: FrameLayout,
    ) {
        if (config.collapsedNativeSmall) {
            if (collapsedAd != null) return
            val preloaded = collapsedPlacement(placement)?.let { nativeAds.takePreloaded(it) }
            if (preloaded != null) {
                collapsedAd = preloaded
                nativeAds.bindTemplate(activity, collapsedSlot, preloaded, NativeTemplate.Small)
                return
            }
            nativeAds.load(activity, config.collapsedUnitId, object : NativeAds.NativeLoadCallback {
                override fun onNextAction() = Unit
                override fun onNativeLoaded(ad: NativeAd) {
                    if (released || activity.isDestroyed) {
                        ad.destroy()
                        return
                    }
                    collapsedAd = ad
                    nativeAds.bindTemplate(activity, collapsedSlot, ad, NativeTemplate.Small)
                }
            })
            return
        }
        val banner = config.collapsedBanner ?: return
        if (collapsedSlot.childCount > 0) return
        AdsSdk.banner.load(activity, collapsedSlot, shimmer = null, banner)
    }

    private fun collapsedPlacement(placement: String?): String? {
        return placement?.let { "${it}_collapsed" }
    }
}
