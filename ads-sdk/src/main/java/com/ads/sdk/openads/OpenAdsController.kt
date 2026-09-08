package com.ads.sdk.openads

import android.app.Activity
import android.content.Context
import com.ads.sdk.AdsSdk
import com.ads.sdk.InterShowReason
import com.ads.sdk.OpenAdFormat
import com.ads.sdk.callback.AdCallback
import com.ads.sdk.internal.SdkLog
import com.ads.sdk.safeNext

class OpenAdsController internal constructor() {

    fun showIfEligible(activity: Activity, callback: AdCallback) {
        val cfg = AdsSdk.config.openAds
        val format = resolveFormat()
        val showOpen = format != OpenAdFormat.Off
        val showFirst = cfg.firstOpenRemoteKey?.let {
            AdsSdk.remoteConfig.getBoolean(it, cfg.firstOpenDefault)
        } ?: cfg.firstOpenDefault
        val first = isFirstOpen(activity)
        val eligible = OpenAdsEligibility.shouldShow(first, showOpen, showFirst)
        SdkLog.d("Open ads first=$first format=$format showFirst=$showFirst eligible=$eligible")
        if (!eligible) {
            markFirstOpenDone(activity)
            callback.safeNext()
            return
        }
        val wrapped = object : AdCallback by callback {
            override fun onNextAction() {
                markFirstOpenDone(activity)
                callback.safeNext()
            }
        }
        if (format == OpenAdFormat.Inter) {
            val unit = AdsSdk.units.interstitial.ifBlank { cfg.interstitialAdUnitId.orEmpty() }
            if (unit.isBlank()) {
                wrapped.onNextAction()
                return
            }
            AdsSdk.interstitial.loadAndShow(
                activity,
                unit,
                ignoreInterval = true,
                callback = wrapped,
                reason = InterShowReason.Open,
            )
        } else {
            val unit = AdsSdk.units.appOpen.ifBlank { cfg.appOpenAdUnitId.orEmpty() }
            if (unit.isBlank()) {
                wrapped.onNextAction()
                return
            }
            AdsSdk.appOpen.load(activity, unit, object : AdCallback by wrapped {
                override fun onAdLoaded() {
                    wrapped.onAdLoaded()
                    AdsSdk.appOpen.show(activity, wrapped)
                }

                override fun onAdFailedToLoad(error: com.ads.sdk.callback.AdError?) {
                    wrapped.onAdFailedToLoad(error)
                    wrapped.onNextAction()
                }

                override fun onNextAction() {
                    // unused; show() will call wrapped
                }
            })
        }
    }

    private fun resolveFormat(): OpenAdFormat {
        val cfg = AdsSdk.config.openAds
        val formatKey = cfg.formatRemoteKey
        if (!formatKey.isNullOrBlank()) {
            return AdsSdk.remote.openAdFormat(formatKey, cfg.formatDefault)
        }
        val showOpen = cfg.enabledRemoteKey?.let {
            AdsSdk.remoteConfig.getBoolean(it, cfg.enabledDefault)
        } ?: cfg.enabledDefault
        if (!showOpen) return OpenAdFormat.Off
        val asInter = cfg.typeIsInterRemoteKey?.let {
            AdsSdk.remoteConfig.getBoolean(it, cfg.typeIsInterDefault)
        } ?: cfg.typeIsInterDefault
        return if (asInter) OpenAdFormat.Inter else OpenAdFormat.AppOpen
    }

    private fun isFirstOpen(context: Context): Boolean {
        return !prefs(context).getBoolean(KEY_DONE, false)
    }

    private fun markFirstOpenDone(context: Context) {
        prefs(context).edit().putBoolean(KEY_DONE, true).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private companion object {
        const val PREFS = "ads_sdk_prefs"
        const val KEY_DONE = "first_open_done"
    }
}
