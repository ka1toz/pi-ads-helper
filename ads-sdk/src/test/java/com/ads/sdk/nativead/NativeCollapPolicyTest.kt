package com.ads.sdk.nativead

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeCollapPolicyTest {

    @Test
    fun zeroMeansStaySmall() {
        assertFalse(NativeCollapPolicy.shouldAutoReexpand(0))
        assertEquals(0L, NativeCollapPolicy.reloadDelayMs(0))
    }

    @Test
    fun positiveReloads() {
        assertTrue(NativeCollapPolicy.shouldAutoReexpand(15))
        assertEquals(15_000L, NativeCollapPolicy.reloadDelayMs(15))
    }

    @Test
    fun homeSlotUsesBannerWhenBothFlagsOff() {
        assertFalse(NativeCollapPolicy.shouldUseCollapsible(showNativeBottom = false, reloadSec = 0))
    }

    @Test
    fun homeSlotUsesCollapWhenNativeBottomOn() {
        assertTrue(NativeCollapPolicy.shouldUseCollapsible(showNativeBottom = true, reloadSec = 0))
    }

    @Test
    fun homeSlotUsesCollapWhenReloadPositive() {
        assertTrue(NativeCollapPolicy.shouldUseCollapsible(showNativeBottom = false, reloadSec = 15))
    }
}
