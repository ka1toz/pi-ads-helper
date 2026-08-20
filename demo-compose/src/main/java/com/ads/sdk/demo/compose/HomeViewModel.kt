package com.ads.sdk.demo.compose

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ads.sdk.AdsSdk
import com.ads.sdk.BannerConfig
import com.ads.sdk.BannerType
import com.ads.sdk.BottomAdConfig
import com.ads.sdk.TestAdUnits
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns *when* ads run (RC flags, interval policy). Does not call GMA show APIs —
 * those need [android.app.Activity] and stay in Compose via [HomeEffect].
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state

    private val _effects = Channel<HomeEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        val rc = AdsSdk.remoteConfig
        val homeBottom = BottomAdConfig(
            showNativeSmall = rc.getBoolean("is_show_native_bottom", true),
            nativeAdUnitId = TestAdUnits.NATIVE,
            banner = BannerConfig(TestAdUnits.ADAPTIVE_BANNER, BannerType.Adaptive),
            collapsible = true,
            reloadSec = rc.getLong("time_reload_collap_ad", 15L).toInt(),
        )
        _state.update {
            it.copy(
                showHomeBottom = rc.getBoolean("is_show_banner_home", true),
                showNative = rc.getBoolean("is_show_native_medium", true),
                homeBottom = homeBottom,
            )
        }
        AdsSdk.native.preload(application, "home_collap", TestAdUnits.NATIVE)
        AdsSdk.native.preload(application, "home_collap_collapsed", TestAdUnits.NATIVE)
    }

    fun onIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.ShowInter -> emitShowInter(ignoreInterval = false)
            HomeIntent.ShowInterForce -> emitShowInter(ignoreInterval = true)
            HomeIntent.ShowRewarded -> viewModelScope.launch {
                _effects.send(HomeEffect.ShowRewarded(TestAdUnits.REWARDED))
            }
            HomeIntent.ToggleResume -> {
                val enabled = !_state.value.resumeEnabled
                _state.update { it.copy(resumeEnabled = enabled) }
                viewModelScope.launch {
                    _effects.send(HomeEffect.SetResumeEnabled(enabled))
                }
            }
        }
    }

    fun onAdResult(message: String) {
        _state.update { it.copy(status = message) }
    }

    fun onAdNextAction() {
        _state.update { it.copy(status = "${it.status} → next") }
    }

    private fun emitShowInter(ignoreInterval: Boolean) {
        val allowed = AdsSdk.remoteConfig.getBoolean("is_show_inter_home", true)
        if (!allowed) {
            onAdResult("inter skipped by RC")
            return
        }
        viewModelScope.launch {
            _effects.send(
                HomeEffect.ShowInter(
                    adUnitId = TestAdUnits.INTERSTITIAL,
                    ignoreInterval = ignoreInterval,
                ),
            )
        }
    }
}
