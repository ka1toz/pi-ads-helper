package com.ads.sdk.remote

import com.ads.sdk.AdsConfig
import com.ads.sdk.Mediation
import com.ads.sdk.OpenAdFormat
import com.ads.sdk.RemoteConfigPolicy
import com.ads.sdk.internal.SdkLog
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings

/**
 * Firebase Remote Config is fetched as **one template**, then read by key.
 * There is no API to fetch a single key from the server.
 *
 * ```
 * AdsSdk.init(..., AdsConfig(remote = RemoteConfigPolicy.FetchAndRead)) { ready ->
 *     val showInter = AdsSdk.remoteConfig.getBoolean("is_show_inter_back_home", false)
 *     val interval = AdsSdk.remoteConfig.getLong("ads_interval", 15)
 * }
 * ```
 */
class AdsRemoteConfig internal constructor(
    private val config: AdsConfig,
) {
    fun applyAsync(onReady: () -> Unit) {
        when (config.remote) {
            RemoteConfigPolicy.None -> onReady()
            RemoteConfigPolicy.ReadOnly -> onReady()
            RemoteConfigPolicy.FetchAndRead -> fetch(onComplete = { onReady() })
        }
    }

    /**
     * Pulls the full RC template then activates it. Call [getBoolean]/[getLong]/[getString] after this.
     */
    fun fetch(onComplete: (success: Boolean) -> Unit = {}) {
        val rc = firebaseOrNull()
        if (rc == null) {
            SdkLog.w("RemoteConfig: Firebase not initialized (add google-services.json)")
            onComplete(false)
            return
        }
        val settings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(if (config.debug) 0 else 3600)
            .build()
        rc.setConfigSettingsAsync(settings)
        rc.fetchAndActivate()
            .addOnCompleteListener { task ->
                SdkLog.d("RemoteConfig fetchAndActivate success=${task.isSuccessful}")
                onComplete(task.isSuccessful)
            }
            .addOnFailureListener { error ->
                SdkLog.w("RemoteConfig fetch failed", error)
                onComplete(false)
            }
    }

    fun setDefaults(values: Map<String, Any>) {
        firebaseOrNull()?.setDefaultsAsync(values)
    }

    fun getBoolean(key: String, default: Boolean = false): Boolean {
        val value = valueOrNull(key) ?: return default
        return runCatching { value.asBoolean() }.getOrDefault(default)
    }

    fun getLong(key: String, default: Long = 0L): Long {
        val value = valueOrNull(key) ?: return default
        return runCatching { value.asLong() }.getOrDefault(default)
    }

    fun getDouble(key: String, default: Double = 0.0): Double {
        val value = valueOrNull(key) ?: return default
        return runCatching { value.asDouble() }.getOrDefault(default)
    }

    fun getString(key: String, default: String = ""): String {
        val value = valueOrNull(key) ?: return default
        return runCatching { value.asString() }.getOrDefault(default)
    }

    fun interstitialIntervalSec(): Int {
        val key = config.interstitialIntervalRemoteKey
        val fromRc = if (key.isNullOrBlank()) {
            config.interstitialIntervalSec.toLong()
        } else {
            getLong(key, config.interstitialIntervalSec.toLong())
        }
        return fromRc.toInt().coerceAtLeast(0)
    }

    fun resumeAdsIntervalSec(): Int {
        val key = config.resumeAdsIntervalRemoteKey
        val fromRc = if (key.isNullOrBlank()) {
            config.resumeAdsIntervalSec.toLong()
        } else {
            getLong(key, config.resumeAdsIntervalSec.toLong())
        }
        return fromRc.toInt().coerceAtLeast(0)
    }

    fun mediationType(): Mediation {
        val key = config.mediationRemoteKey
        val raw = if (key.isNullOrBlank()) {
            config.mediationDefault
        } else {
            mediationRaw(key, config.mediationDefault)
        }
        return Mediation.fromRemote(raw)
    }

    fun openAdFormat(key: String?, default: OpenAdFormat): OpenAdFormat {
        if (key.isNullOrBlank()) return default
        val value = valueOrNull(key) ?: return default
        val asString = runCatching { value.asString() }.getOrNull()
        return OpenAdFormat.parse(asString, default)
    }

    private fun mediationRaw(key: String, default: Int): Int {
        val value = valueOrNull(key) ?: return default
        runCatching { value.asLong().toInt() }.getOrNull()?.let { return it }
        val asString = runCatching { value.asString() }.getOrNull()?.trim().orEmpty()
        if (asString.isNotEmpty()) {
            asString.toDoubleOrNull()?.toInt()?.let { return it }
        }
        return default
    }

    private fun valueOrNull(key: String) = firebaseOrNull()?.getValue(key)?.takeIf {
        it.source != FirebaseRemoteConfig.VALUE_SOURCE_STATIC
    }

    private fun firebaseOrNull(): FirebaseRemoteConfig? {
        return runCatching { FirebaseRemoteConfig.getInstance() }.getOrNull()
    }
}
