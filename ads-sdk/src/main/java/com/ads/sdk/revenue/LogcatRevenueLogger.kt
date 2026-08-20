package com.ads.sdk.revenue

import android.util.Log
import com.ads.sdk.FunnelEvent
import com.ads.sdk.FunnelLogger
import com.ads.sdk.PaidAdEvent
import com.ads.sdk.RevenueLogger

class LogcatRevenueLogger : RevenueLogger {
    override fun onPaid(event: PaidAdEvent) {
        Log.i(TAG, "ad_impression ${PaidEventMapper.toFirebaseParams(event)}")
    }

    private companion object {
        const val TAG = "AdsSdk.Revenue"
    }
}

class LogcatFunnelLogger : FunnelLogger {
    override fun onEvent(event: FunnelEvent) {
        Log.i("AdsSdk.Funnel", event.key)
    }
}
