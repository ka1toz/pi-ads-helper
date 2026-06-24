package com.jbase.demo

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.unity3d.player.UnityPlayer

class JBaseDemoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                UnityPlayer.currentActivity = activity
            }

            override fun onActivityStarted(activity: Activity) {
                UnityPlayer.currentActivity = activity
            }

            override fun onActivityResumed(activity: Activity) {
                UnityPlayer.currentActivity = activity
            }

            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

            override fun onActivityDestroyed(activity: Activity) {
                if (UnityPlayer.currentActivity === activity) {
                    UnityPlayer.currentActivity = null
                }
            }
        })
    }
}
