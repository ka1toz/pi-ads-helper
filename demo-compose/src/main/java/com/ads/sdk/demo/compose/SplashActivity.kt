package com.ads.sdk.demo.compose

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ads.sdk.AdsSdk
import com.ads.sdk.TestAdUnits
import com.ads.sdk.callback.AdCallback

class SplashActivity : ComponentActivity() {
    private var navigated = false
    private var status by mutableStateOf("Consent…")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AdsSdk.appOpen.disableResumeWith(SplashActivity::class.java)
        setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(status)
                }
            }
            LaunchedEffect(Unit) {
                AdsSdk.consent.obtainAndShow(this@SplashActivity) {
                    status = "Open ads…"
                    AdsSdk.native.preload(applicationContext, "home_collap", TestAdUnits.NATIVE)
                    AdsSdk.native.preload(applicationContext, "home_collap_collapsed", TestAdUnits.NATIVE)
                    AdsSdk.openAds.showIfEligible(
                        this@SplashActivity,
                        object : AdCallback {
                            override fun onNextAction() = goHome()
                        },
                    )
                }
            }
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
