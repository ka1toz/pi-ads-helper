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
    data object OpenAdInspector : HomeIntent
}

/** One-shot commands that need Activity. ViewModel never holds Activity. */
sealed interface HomeEffect {
    data class ShowInter(
        val adUnitId: String,
        val ignoreInterval: Boolean,
    ) : HomeEffect

    data class ShowRewarded(val adUnitId: String) : HomeEffect

    data class SetResumeEnabled(val enabled: Boolean) : HomeEffect

    data object OpenAdInspector : HomeEffect
}

data class HomeUiState(
    val status: String = "Ready",
    val resumeEnabled: Boolean = true,
    val showAdInspector: Boolean = false,
    val showHomeBottom: Boolean = true,
    val showNative: Boolean = true,
    val showPirago: Boolean = true,
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
    val piragoMediumLayout: Int = R.layout.layout_native_ad_pirago_medium,
    val piragoFullscreenLayout: Int = R.layout.layout_native_ad_pirago_fullscreen,
    val diyCustomLayout: Int = R.layout.layout_native_ad_custom,
)

@Composable
fun HomeUi(
    state: HomeUiState,
    onIntent: (HomeIntent) -> Unit,
    modifier: Modifier = Modifier,
    homeBottomSlot: @Composable () -> Unit = {},
    nativeMediumSlot: @Composable () -> Unit = {},
    diyCustomSlot: @Composable () -> Unit = {},
    piragoMediumSlot: @Composable () -> Unit = {},
    piragoFullscreenSlot: @Composable () -> Unit = {},
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
        if (state.showAdInspector) {
            Button(
                onClick = { onIntent(HomeIntent.OpenAdInspector) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Open Ad Inspector") }
        }
        if (state.showHomeBottom) {
            Text("Home bottom (Pirago medium ↔ small)", style = MaterialTheme.typography.titleMedium)
            homeBottomSlot()
        }
        if (state.showNative) {
            Text("Native medium (SDK template)", style = MaterialTheme.typography.titleMedium)
            nativeMediumSlot()
            Text("Native DIY custom (adHeadline)", style = MaterialTheme.typography.titleMedium)
            diyCustomSlot()
        }
        if (state.showPirago) {
            Text("Native Pirago medium", style = MaterialTheme.typography.titleMedium)
            piragoMediumSlot()
            Text("Native Pirago fullscreen (adAppIcon)", style = MaterialTheme.typography.titleMedium)
            piragoFullscreenSlot()
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
                diyCustomSlot = { AdSlotPlaceholder("DIY custom", 120) },
                piragoMediumSlot = { AdSlotPlaceholder("Pirago medium", 220) },
                piragoFullscreenSlot = { AdSlotPlaceholder("Pirago fullscreen", 260) },
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
                    showPirago = false,
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
                    showPirago = false,
                ),
                onIntent = {},
                nativeMediumSlot = { AdSlotPlaceholder("Native medium", 220) },
                diyCustomSlot = { AdSlotPlaceholder("DIY custom", 120) },
            )
        }
    }
}
