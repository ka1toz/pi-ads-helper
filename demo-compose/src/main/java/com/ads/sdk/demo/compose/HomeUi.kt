package com.ads.sdk.demo.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ads.sdk.BannerConfig
import com.ads.sdk.BannerType
import com.ads.sdk.BottomAdConfig
import com.ads.sdk.NativeTemplate
import com.ads.sdk.TestAdUnits

sealed interface HomeIntent {
    data object ShowInter : HomeIntent
    data object ShowInterForce : HomeIntent
    data object ShowRewarded : HomeIntent
    data object ToggleResume : HomeIntent
}

/** One-shot commands that need Activity. ViewModel never holds Activity. */
sealed interface HomeEffect {
    data class ShowInter(
        val adUnitId: String,
        val ignoreInterval: Boolean,
    ) : HomeEffect

    data class ShowRewarded(val adUnitId: String) : HomeEffect

    data class SetResumeEnabled(val enabled: Boolean) : HomeEffect
}

data class HomeUiState(
    val status: String = "Ready",
    val resumeEnabled: Boolean = true,
    val showHomeBottom: Boolean = true,
    val showNative: Boolean = true,
    val homeBottom: BottomAdConfig = BottomAdConfig(
        showNativeSmall = true,
        nativeAdUnitId = TestAdUnits.NATIVE,
        banner = BannerConfig(TestAdUnits.ADAPTIVE_BANNER, BannerType.Adaptive),
        collapsible = true,
        reloadSec = 15,
    ),
    val homeBottomPlacement: String = "home_collap",
    val nativeUnitId: String = TestAdUnits.NATIVE,
    val nativeTemplate: NativeTemplate = NativeTemplate.Medium,
)

@Composable
fun HomeUi(
    state: HomeUiState,
    onIntent: (HomeIntent) -> Unit,
    modifier: Modifier = Modifier,
    homeBottomSlot: @Composable () -> Unit = {},
    nativeMediumSlot: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Ads SDK — Compose + ViewModel", style = MaterialTheme.typography.titleLarge)
        Text(state.status, style = MaterialTheme.typography.bodyMedium)
        Button(
            onClick = { onIntent(HomeIntent.ShowInter) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Show interstitial") }
        Button(
            onClick = { onIntent(HomeIntent.ShowInterForce) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Show interstitial (ignore interval)") }
        Button(
            onClick = { onIntent(HomeIntent.ShowRewarded) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Load + show rewarded") }
        Button(
            onClick = { onIntent(HomeIntent.ToggleResume) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.resumeEnabled) "Disable resume ads" else "Enable resume ads")
        }
        if (state.showHomeBottom) {
            Text("Home bottom (collap ↔ small/banner)", style = MaterialTheme.typography.titleMedium)
            homeBottomSlot()
        }
        if (state.showNative) {
            Text("Native medium", style = MaterialTheme.typography.titleMedium)
            nativeMediumSlot()
        }
    }
}

@Composable
internal fun AdSlotPlaceholder(label: String, height: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height.dp)
            .background(Color(0xFFE2E8F0)),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color(0xFF475569), style = MaterialTheme.typography.labelLarge)
    }
}

@Preview(name = "Home — ads on", showBackground = true, showSystemUi = true)
@Composable
private fun HomeUiAdsOnPreview() {
    MaterialTheme {
        Surface {
            HomeUi(
                state = HomeUiState(status = "Ready"),
                onIntent = {},
                homeBottomSlot = { AdSlotPlaceholder("Home bottom", 200) },
                nativeMediumSlot = { AdSlotPlaceholder("Native medium", 220) },
            )
        }
    }
}

@Preview(name = "Home — ads off (RC)", showBackground = true, showSystemUi = true)
@Composable
private fun HomeUiAdsOffPreview() {
    MaterialTheme {
        Surface {
            HomeUi(
                state = HomeUiState(
                    status = "inter skipped by RC",
                    showHomeBottom = false,
                    showNative = false,
                ),
                onIntent = {},
            )
        }
    }
}

@Preview(name = "Home — resume disabled", showBackground = true)
@Composable
private fun HomeUiResumeOffPreview() {
    MaterialTheme {
        Surface {
            HomeUi(
                state = HomeUiState(
                    status = "inter shown → next",
                    resumeEnabled = false,
                    showHomeBottom = false,
                ),
                onIntent = {},
                nativeMediumSlot = { AdSlotPlaceholder("Native medium", 220) },
            )
        }
    }
}
