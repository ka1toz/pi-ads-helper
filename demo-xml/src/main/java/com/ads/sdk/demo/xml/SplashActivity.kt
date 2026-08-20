package com.ads.sdk.demo.xml

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.ads.sdk.AdsSdk
import com.ads.sdk.TestAdUnits
import com.ads.sdk.callback.AdCallback

class SplashActivity : AppCompatActivity() {
    private var navigated = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        AdsSdk.appOpen.disableResumeWith(SplashActivity::class.java)

        val status = findViewById<TextView>(R.id.splashStatus)
        AdsSdk.consent.obtainAndShow(this) {
            status.text = "Consent done — open ads…"
            AdsSdk.native.preload(applicationContext, "home_collap", TestAdUnits.NATIVE)
            AdsSdk.native.preload(applicationContext, "home_collap_collapsed", TestAdUnits.NATIVE)
            AdsSdk.openAds.showIfEligible(this, object : AdCallback {
                override fun onNextAction() = goHome()
            })
        }
    }

    override fun onResume() {
        super.onResume()
        AdsSdk.splash.retryOnResume(this, object : AdCallback {
            override fun onNextAction() = goHome()
        }, delayMs = 300)
    }

    private fun goHome() {
        if (navigated || isFinishing) return
        navigated = true
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
