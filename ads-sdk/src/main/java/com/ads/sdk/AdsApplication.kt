package com.ads.sdk

import android.app.Application

/**
 * Convenience base class for apps that do not use Hilt.
 * Hilt apps should call [AdsSdk.init] from `Application.onCreate` instead.
 */
open class AdsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AdsSdk.init(this, buildAdsConfig())
    }

    protected open fun buildAdsConfig(): AdsConfig = AdsConfig(debug = BuildConfig.DEBUG)
}
