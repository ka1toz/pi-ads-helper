package com.unity3d.player

import android.app.Activity

/**
 * Compatibility shim for the existing JBase AARs.
 *
 * The original Unity integration reads UnityPlayer.currentActivity directly.
 * This demo updates the field from Activity lifecycle callbacks so the same
 * binary AARs can run in a normal Android app without the Unity runtime.
 */
object UnityPlayer {
    @JvmField
    var currentActivity: Activity? = null
}
