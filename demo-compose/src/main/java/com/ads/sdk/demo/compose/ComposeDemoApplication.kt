package com.ads.sdk.demo.compose

import android.app.Application
import com.ads.sdk.AdsConfig
import com.ads.sdk.AdsSdk
import com.ads.sdk.OpenAdsConfig
import com.ads.sdk.RemoteConfigPolicy
import com.ads.sdk.ResumeFormat
import com.ads.sdk.TestAdUnits
import com.ads.sdk.revenue.LogcatFunnelLogger
import com.ads.sdk.revenue.LogcatRevenueLogger

class ComposeDemoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AdsSdk.init(
            this,
            AdsConfig(
                debug = true,
                interstitialIntervalSec = 15,
                enableResumeAds = true,
                resumeAdUnitId = TestAdUnits.APP_OPEN,
                resumeFormat = ResumeFormat.AppOpen,
                openAds = OpenAdsConfig(
                    appOpenAdUnitId = TestAdUnits.APP_OPEN,
                    interstitialAdUnitId = TestAdUnits.INTERSTITIAL,
                    enabledDefault = true,
                    firstOpenDefault = true,
                    typeIsInterDefault = false,
                ),
                remote = RemoteConfigPolicy.None,
                revenueLogger = LogcatRevenueLogger(),
                funnelLogger = LogcatFunnelLogger(),
            ),
        )
        AdsSdk.consent.reset(this)
    }
}
