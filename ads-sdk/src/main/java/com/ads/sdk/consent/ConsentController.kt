package com.ads.sdk.consent

import android.app.Activity
import android.content.Context
import com.ads.sdk.AdsSdk
import com.ads.sdk.internal.MainThread
import com.ads.sdk.internal.SdkLog
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

class ConsentController internal constructor() {

    @Volatile
    private var started = false

    @Volatile
    private var ready = false

    private val lock = Any()
    private val pending = mutableListOf<() -> Unit>()

    fun canRequestAds(context: Context): Boolean {
        return UserMessagingPlatform.getConsentInformation(context).canRequestAds()
    }

    fun canRequestAds(): Boolean {
        val activity = AdsSdk.currentActivity() ?: return false
        return canRequestAds(activity)
    }

    fun obtainAndShow(activity: Activity, onComplete: () -> Unit) {
        val paramsBuilder = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
        val config = AdsSdk.config
        if (config.debug) {
            val debug = ConsentDebugSettings.Builder(activity)
            if (config.debugConsentEea) {
                debug.setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
            }
            testDeviceIds().forEach { debug.addTestDeviceHashedId(it) }
            paramsBuilder.setConsentDebugSettings(debug.build())
        }
        val consentInfo = UserMessagingPlatform.getConsentInformation(activity)
        consentInfo.requestConsentInfoUpdate(
            activity,
            paramsBuilder.build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) {
                        SdkLog.w("Consent form error ${formError.errorCode}: ${formError.message}")
                    }
                    finish(activity, onComplete)
                }
            },
            { error ->
                SdkLog.w("Consent info update failed ${error.errorCode}: ${error.message}")
                finish(activity, onComplete)
            },
        )
    }

    fun reset(context: Context) {
        UserMessagingPlatform.getConsentInformation(context).reset()
        synchronized(lock) {
            started = false
            ready = false
            pending.clear()
        }
    }

    private fun finish(activity: Activity, onComplete: () -> Unit) {
        initializeMobileAds(activity.applicationContext) { onComplete() }
    }

    internal fun initializeMobileAds(context: Context, onReady: (() -> Unit)? = null) {
        val app = context.applicationContext
        synchronized(lock) {
            if (ready) {
                if (onReady != null) MainThread.post(onReady)
                return
            }
            if (onReady != null) pending.add(onReady)
            if (started) return
            started = true
        }
        val devices = testDeviceIds()
        if (devices.isNotEmpty()) {
            MobileAds.setRequestConfiguration(
                RequestConfiguration.Builder()
                    .setTestDeviceIds(devices)
                    .build(),
            )
        }
        MobileAds.initialize(app) {
            SdkLog.d("MobileAds initialized")
            val waiters: List<() -> Unit>
            synchronized(lock) {
                ready = true
                waiters = pending.toList()
                pending.clear()
            }
            MainThread.post { waiters.forEach { it() } }
        }
    }

    private fun testDeviceIds(): List<String> {
        val config = AdsSdk.configOrNull ?: return emptyList()
        val ids = config.testDeviceIds.toMutableList()
        if (config.debug && AdRequest.DEVICE_ID_EMULATOR !in ids) {
            ids.add(AdRequest.DEVICE_ID_EMULATOR)
        }
        return ids
    }
}
