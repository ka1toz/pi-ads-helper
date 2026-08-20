package com.ads.sdk.openads

import android.app.Activity
import android.content.Context
import com.ads.sdk.AdsSdk
import com.ads.sdk.InterShowReason
import com.ads.sdk.callback.AdCallback
import com.ads.sdk.internal.SdkLog
import com.ads.sdk.safeNext

class OpenAdsController internal constructor() {

    fun showIfEligible(activity: Activity, callback: AdCallback) {
        val cfg = AdsSdk.config.openAds
        val rc = AdsSdk.remoteConfig
        val showOpen = cfg.enabledRemoteKey?.let { rc.getBoolean(it, cfg.enabledDefault) } ?: cfg.enabledDefault
        val showFirst = cfg.firstOpenRemoteKey?.let { rc.getBoolean(it, cfg.firstOpenDefault) } ?: cfg.firstOpenDefault
        val asInter = cfg.typeIsInterRemoteKey?.let { rc.getBoolean(it, cfg.typeIsInterDefault) } ?: cfg.typeIsInterDefault
        val first = isFirstOpen(activity)
        val eligible = OpenAdsEligibility.shouldShow(first, showOpen, showFirst)
        SdkLog.d("Open ads first=$first showOpen=$showOpen showFirst=$showFirst asInter=$asInter eligible=$eligible")
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
        if (asInter) {
            val unit = cfg.interstitialAdUnitId
            if (unit.isNullOrBlank()) {
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
            val unit = cfg.appOpenAdUnitId
            if (unit.isNullOrBlank()) {
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
