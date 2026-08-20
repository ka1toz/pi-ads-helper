package com.ads.sdk.internal

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.ads.sdk.AdsSdk

internal object SdkLog {
    private const val TAG = "AdsSdk"
    fun d(msg: String) {
        if (AdsSdk.configOrNull?.debug == true) Log.d(TAG, msg)
    }
    fun w(msg: String, t: Throwable? = null) {
        Log.w(TAG, msg, t)
    }
    fun e(msg: String, t: Throwable? = null) {
        Log.e(TAG, msg, t)
    }
}

internal object MainThread {
    private val handler = Handler(Looper.getMainLooper())

    fun post(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else handler.post(block)
    }

    fun postDelayed(delayMs: Long, runnable: Runnable) {
        handler.postDelayed(runnable, delayMs)
    }

    fun remove(runnable: Runnable) {
        handler.removeCallbacks(runnable)
    }
}
