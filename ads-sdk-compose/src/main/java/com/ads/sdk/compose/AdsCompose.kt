package com.ads.sdk.compose

import android.app.Activity
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.LocalActivity
import androidx.annotation.LayoutRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.ads.sdk.AdsSdk
import com.ads.sdk.BannerConfig
import com.ads.sdk.BottomAdConfig
import com.ads.sdk.NativeCollapConfig
import com.ads.sdk.NativeTemplate

@Composable
fun AdsBanner(
    config: BannerConfig,
    modifier: Modifier = Modifier,
) {
    val activity = LocalActivity.current as Activity
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { ctx ->
            FrameLayout(ctx).also { AdsSdk.banner.load(activity, it, shimmer = null, config) }
        },
        onRelease = { AdsSdk.banner.destroy(it) },
    )
}

@Composable
fun AdsNative(
    adUnitId: String,
    template: NativeTemplate = NativeTemplate.Medium,
    modifier: Modifier = Modifier,
    placement: String? = null,
) {
    val activity = LocalActivity.current as Activity
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { ctx ->
            FrameLayout(ctx).also { container ->
                AdsSdk.native.loadAndBind(
                    activity = activity,
                    container = container,
                    adUnitId = adUnitId,
                    template = template,
                    placement = placement,
                )
            }
        },
        onRelease = { AdsSdk.native.destroy(it) },
    )
}

@Composable
fun AdsNative(
    adUnitId: String,
    @LayoutRes layoutRes: Int,
    modifier: Modifier = Modifier,
    placement: String? = null,
) {
    val activity = LocalActivity.current as Activity
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { ctx ->
            FrameLayout(ctx).also { container ->
                AdsSdk.native.loadAndBind(
                    activity = activity,
                    container = container,
                    adUnitId = adUnitId,
                    layoutRes = layoutRes,
                    placement = placement,
                )
            }
        },
        onRelease = { AdsSdk.native.destroy(it) },
    )
}

@Composable
fun AdsNativeCollapsible(
    config: NativeCollapConfig,
    modifier: Modifier = Modifier,
    placement: String? = null,
) {
    val activity = LocalActivity.current as Activity
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { ctx ->
            FrameLayout(ctx).also { AdsSdk.native.loadCollapsible(activity, it, config, placement) }
        },
        onRelease = { AdsSdk.native.destroy(it) },
    )
}

@Composable
fun AdsBottom(
    config: BottomAdConfig,
    modifier: Modifier = Modifier,
    placement: String? = null,
) {
    val activity = LocalActivity.current as Activity
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { ctx ->
            FrameLayout(ctx).also { AdsSdk.bottom.load(activity, it, config, placement = placement) }
        },
        onRelease = { container: ViewGroup ->
            AdsSdk.banner.destroy(container)
            AdsSdk.native.destroy(container)
        },
    )
}
