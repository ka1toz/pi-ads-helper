package com.ads.sdk.nativead

internal object NativeCollapPolicy {
    /**
     * DIY Home gate: collapsible native only if bottom-native is on **or**
     * `time_reload_collap_ad` is a positive number. Otherwise the slot is a
     * plain banner.
     */
    fun shouldUseCollapsible(showNativeBottom: Boolean, reloadSec: Int): Boolean {
        return showNativeBottom || reloadSec > 0
    }

    /**
     * 0 = after collapse stay on Small/Banner, never auto re-expand.
     * >0 = re-expand (and reload) after that many seconds.
     */
    fun shouldAutoReexpand(reloadSec: Int): Boolean = reloadSec > 0

    fun reloadDelayMs(reloadSec: Int): Long = reloadSec.coerceAtLeast(0) * 1000L
}
