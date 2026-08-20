package com.ads.sdk.lifecycle

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.ads.sdk.internal.SdkLog
import java.lang.ref.WeakReference

internal class SdkLifecycle(
    application: Application,
    private val onProcessStart: (Activity?) -> Unit,
) : Application.ActivityLifecycleCallbacks, DefaultLifecycleObserver {

    private var current: WeakReference<Activity>? = null
    private var startedCount = 0
    @Volatile
    var skipNextProcessStart: Boolean = true

    val currentActivity: Activity?
        get() = current?.get()?.takeUnless { it.isFinishing || it.isDestroyed }

    init {
        application.registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        if (skipNextProcessStart) {
            skipNextProcessStart = false
            SdkLog.d("Skip resume ads on cold start")
            return
        }
        onProcessStart(currentActivity)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityStarted(activity: Activity) {
        startedCount++
        current = WeakReference(activity)
    }

    override fun onActivityResumed(activity: Activity) {
        current = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) {
        startedCount--
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) {
        if (current?.get() === activity) current = null
    }
}
