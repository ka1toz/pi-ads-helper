package com.ads.sdk.splash

import android.app.Activity
import android.app.ProgressDialog
import com.ads.sdk.AdsSdk
import com.ads.sdk.InterShowReason
import com.ads.sdk.callback.AdCallback
import com.ads.sdk.callback.AdError
import com.ads.sdk.internal.MainThread
import com.ads.sdk.internal.SdkLog
import com.ads.sdk.safeNext

class SplashAds internal constructor() {

    @Volatile
    private var timedOut = false
    @Volatile
    private var finished = false
    @Volatile
    private var pendingRetry = false
    private var dialog: ProgressDialog? = null
    private var timeoutRunnable: Runnable? = null

    fun load(
        activity: Activity,
        adUnitId: String,
        timeoutMs: Long,
        callback: AdCallback,
        showLoading: Boolean = false,
    ) {
        timedOut = false
        finished = false
        pendingRetry = false
        if (showLoading) {
            dialog = ProgressDialog(activity).apply {
                setMessage("Loading ads…")
                setCancelable(false)
                show()
            }
        }
        timeoutRunnable = Runnable {
            if (finished) return@Runnable
            timedOut = true
            pendingRetry = true
            SdkLog.d("Splash timeout ${timeoutMs}ms")
            dismissLoadingDialog()
            callback.safeNext()
        }
        MainThread.postDelayed(timeoutMs, timeoutRunnable!!)

        AdsSdk.interstitial.loadAndShow(
            activity,
            adUnitId,
            ignoreInterval = true,
            callback = object : AdCallback by callback {
                override fun onNextAction() {
                    if (finished) return
                    if (timedOut) {
                        pendingRetry = true
                        return
                    }
                    finish(callback)
                }

                override fun onAdFailedToLoad(error: AdError?) {
                    callback.onAdFailedToLoad(error)
                    pendingRetry = true
                }
            },
            reason = InterShowReason.Open,
        )
    }

    fun retryOnResume(activity: Activity, callback: AdCallback, delayMs: Long = 0) {
        if (!pendingRetry) return
        MainThread.postDelayed(delayMs) {
            if (!pendingRetry || finished) return@postDelayed
            if (AdsSdk.interstitial.isReady()) {
                AdsSdk.interstitial.show(
                    activity,
                    object : AdCallback by callback {
                        override fun onNextAction() = finish(callback)
                    },
                    reason = InterShowReason.Open,
                )
            } else {
                finish(callback)
            }
        }
    }

    fun dismissLoadingDialog() {
        runCatching { dialog?.dismiss() }
        dialog = null
    }

    private fun finish(callback: AdCallback) {
        if (finished) return
        finished = true
        pendingRetry = false
        timeoutRunnable?.let { MainThread.remove(it) }
        timeoutRunnable = null
        dismissLoadingDialog()
        callback.safeNext()
    }
}
