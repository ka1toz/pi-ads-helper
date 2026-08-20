package com.ads.sdk.internal

/**
 * Interstitial cooldown. Open-as-inter and resume-as-inter must pass [ignore]=true.
 * Interval starts at dismiss of a content interstitial.
 */
internal class IntervalGate(
    private val intervalMs: () -> Long,
    private val nowMs: () -> Long,
) {
    @Volatile
    private var lastContentDismissAt: Long = 0L

    fun markContentDismissed() {
        lastContentDismissAt = nowMs()
    }

    fun canShow(ignore: Boolean): Boolean {
        if (ignore) return true
        if (lastContentDismissAt == 0L) return true
        return nowMs() - lastContentDismissAt >= intervalMs()
    }

    fun remainingMs(): Long {
        if (lastContentDismissAt == 0L) return 0L
        val left = intervalMs() - (nowMs() - lastContentDismissAt)
        return left.coerceAtLeast(0L)
    }
}
