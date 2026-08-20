package com.ads.sdk.demo.xml

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.ads.sdk.AdsSdk
import com.ads.sdk.BannerConfig
import com.ads.sdk.BannerType
import com.ads.sdk.BottomAdConfig
import com.ads.sdk.NativeTemplate
import com.ads.sdk.TestAdUnits
import com.ads.sdk.callback.AdCallback
import com.ads.sdk.callback.AdError

class MainActivity : AppCompatActivity() {
    private var resumeEnabled = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val status = findViewById<TextView>(R.id.status)

        AdsSdk.bottom.load(
            this,
            findViewById(R.id.homeBottom),
            homeBottomConfig(),
            placement = "home_collap",
            callback = logCallback(status, "home-bottom"),
        )
        AdsSdk.native.loadAndBind(
            this,
            findViewById(R.id.nativeMedium),
            TestAdUnits.NATIVE,
            NativeTemplate.Medium,
            callback = logCallback(status, "native-medium"),
        )
        AdsSdk.native.loadAndBind(
            this,
            findViewById(R.id.nativeCustom),
            TestAdUnits.NATIVE,
            R.layout.layout_native_ad_custom,
            callback = logCallback(status, "native-custom"),
        )

        findViewById<Button>(R.id.btnInter).setOnClickListener {
            AdsSdk.interstitial.loadAndShow(
                this,
                TestAdUnits.INTERSTITIAL,
                ignoreInterval = false,
                callback = logCallback(status, "inter"),
            )
        }
        findViewById<Button>(R.id.btnInterForce).setOnClickListener {
            AdsSdk.interstitial.loadAndShow(
                this,
                TestAdUnits.INTERSTITIAL,
                ignoreInterval = true,
                callback = logCallback(status, "inter-force"),
            )
        }
        findViewById<Button>(R.id.btnRewarded).setOnClickListener {
            AdsSdk.rewarded.load(this, TestAdUnits.REWARDED, object : AdCallback {
                override fun onNextAction() = Unit
                override fun onAdLoaded() {
                    AdsSdk.rewarded.show(this@MainActivity, logCallback(status, "rewarded"))
                }
                override fun onAdFailedToLoad(error: AdError?) {
                    status.text = "rewarded fail: ${error?.message}"
                }
            })
        }
        val toggle = findViewById<Button>(R.id.btnToggleResume)
        toggle.setOnClickListener {
            resumeEnabled = !resumeEnabled
            if (resumeEnabled) AdsSdk.appOpen.enableResume() else AdsSdk.appOpen.disableResume()
            toggle.text = if (resumeEnabled) "Disable resume ads" else "Enable resume ads"
        }
    }

    private fun homeBottomConfig(): BottomAdConfig {
        val rc = AdsSdk.remoteConfig
        return BottomAdConfig(
            showNativeSmall = rc.getBoolean("is_show_native_bottom", true),
            nativeAdUnitId = TestAdUnits.NATIVE,
            banner = BannerConfig(TestAdUnits.ADAPTIVE_BANNER, BannerType.Adaptive),
            collapsible = true,
            reloadSec = rc.getLong("time_reload_collap_ad", 15L).toInt(),
        )
    }

    private fun logCallback(status: TextView, label: String) = object : AdCallback {
        override fun onNextAction() {
            status.text = "$label next"
        }
        override fun onAdLoaded() {
            status.text = "$label loaded"
        }
        override fun onAdShown() {
            status.text = "$label shown"
        }
        override fun onUserEarnedReward(amount: Int, type: String) {
            status.text = "$label reward $amount $type"
        }
        override fun onAdFailedToLoad(error: AdError?) {
            status.text = "$label fail: ${error?.message}"
        }
    }
}
