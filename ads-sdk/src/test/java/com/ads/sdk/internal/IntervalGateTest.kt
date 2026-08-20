package com.ads.sdk.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntervalGateTest {

    @Test
    fun firstShowAlwaysAllowed() {
        val gate = IntervalGate(intervalMs = { 15_000 }, nowMs = { 0 })
        assertTrue(gate.canShow(ignore = false))
    }

    @Test
    fun contentCooldownBlocksUntilIntervalElapses() {
        var now = 1_000L
        val gate = IntervalGate(intervalMs = { 15_000 }, nowMs = { now })
        gate.markContentDismissed()
        now = 10_000L
        assertFalse(gate.canShow(ignore = false))
        now = 16_000L
        assertTrue(gate.canShow(ignore = false))
    }

    @Test
    fun openAndResumeIgnoreCooldown() {
        var now = 1_000L
        val gate = IntervalGate(intervalMs = { 15_000 }, nowMs = { now })
        gate.markContentDismissed()
        now = 2_000L
        assertTrue(gate.canShow(ignore = true))
        assertEquals(14_000L, gate.remainingMs())
    }
}
