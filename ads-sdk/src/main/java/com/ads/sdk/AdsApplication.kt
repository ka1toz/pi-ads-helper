package com.ads.sdk

import android.app.Application

/**
 * Optional [Application] base that calls [AdsSdk.init].
 *
 * Koin (and Hilt) apps should **not** extend this class — call [AdsSdk.init]
 * from `Application.onCreate` next to `startKoin` / `@HiltAndroidApp`.
 */
open class AdsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AdsSdk.init(this, buildAdsConfig())
    }

    protected open fun buildAdsConfig(): AdsConfig = AdsConfig(debug = BuildConfig.DEBUG)
}
