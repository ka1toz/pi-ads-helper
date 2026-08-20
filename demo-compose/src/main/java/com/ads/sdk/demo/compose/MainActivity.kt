package com.ads.sdk.demo.compose

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ads.sdk.AdsSdk
import com.ads.sdk.callback.AdCallback
import com.ads.sdk.callback.AdError
import com.ads.sdk.compose.AdsBottom
import com.ads.sdk.compose.AdsNative

class MainActivity : ComponentActivity() {
    private val viewModel: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                HomeScreen(viewModel)
            }
        }
    }
}

@Composable
private fun HomeScreen(viewModel: HomeViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalActivity.current as Activity

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            handleAdEffect(activity, effect, viewModel)
        }
    }

    HomeUi(
        state = state,
        onIntent = viewModel::onIntent,
        homeBottomSlot = {
            AdsBottom(config = state.homeBottom, placement = state.homeBottomPlacement)
        },
        nativeMediumSlot = {
            AdsNative(adUnitId = state.nativeUnitId, template = state.nativeTemplate)
        },
    )
}

private fun handleAdEffect(activity: Activity, effect: HomeEffect, viewModel: HomeViewModel) {
    when (effect) {
        is HomeEffect.ShowInter -> AdsSdk.interstitial.loadAndShow(
            activity,
            effect.adUnitId,
            ignoreInterval = effect.ignoreInterval,
            callback = adCallback(viewModel, "inter"),
        )
        is HomeEffect.ShowRewarded -> AdsSdk.rewarded.load(
            activity,
            effect.adUnitId,
            object : AdCallback {
                override fun onNextAction() = Unit
                override fun onAdLoaded() {
                    AdsSdk.rewarded.show(activity, adCallback(viewModel, "rewarded"))
                }
                override fun onAdFailedToLoad(error: AdError?) {
                    viewModel.onAdResult("rewarded fail: ${error?.message}")
                }
            },
        )
        is HomeEffect.SetResumeEnabled -> {
            if (effect.enabled) AdsSdk.appOpen.enableResume() else AdsSdk.appOpen.disableResume()
        }
    }
}

private fun adCallback(viewModel: HomeViewModel, label: String) = object : AdCallback {
    override fun onNextAction() = viewModel.onAdNextAction()
    override fun onAdShown() = viewModel.onAdResult("$label shown")
    override fun onUserEarnedReward(amount: Int, type: String) {
        viewModel.onAdResult("$label reward $amount $type")
    }
    override fun onAdFailedToLoad(error: AdError?) {
        viewModel.onAdResult("$label fail: ${error?.message}")
    }
}
