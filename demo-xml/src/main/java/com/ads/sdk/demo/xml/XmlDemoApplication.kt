package com.ads.sdk.demo.xml

import android.app.Application
import com.ads.sdk.AdsConfig
import com.ads.sdk.AdsSdk
import com.ads.sdk.OpenAdsConfig
import com.ads.sdk.RemoteConfigPolicy
import com.ads.sdk.ResumeFormat
import com.ads.sdk.TestAdUnits
import com.ads.sdk.revenue.LogcatFunnelLogger
import com.ads.sdk.revenue.LogcatRevenueLogger

class XmlDemoApplication : Application() {
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
                remote = RemoteConfigPolicy.FetchAndRead,
                revenueLogger = LogcatRevenueLogger(),
                funnelLogger = LogcatFunnelLogger(),
            ),
        ) {
            // TODO: fetch remote config ở đây
        }
        AdsSdk.consent.reset(this)
    }
}
