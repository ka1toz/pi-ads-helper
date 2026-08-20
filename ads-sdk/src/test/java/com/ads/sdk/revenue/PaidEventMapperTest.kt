package com.ads.sdk.revenue

import com.ads.sdk.PaidAdEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class PaidEventMapperTest {

    @Test
    fun firebaseParamsMatchGoogleManualImpressionSpec() {
        val event = PaidAdEvent(
            valueMicros = 1_500_000,
            currencyCode = "USD",
            precision = 3,
            adSource = "AdMob",
            adFormat = "interstitial",
            adUnitId = "ca-app-pub-xxx/yyy",
        )
        val params = PaidEventMapper.toFirebaseParams(event)
        assertEquals("AdMob", params["ad_platform"])
        assertEquals("AdMob", params["ad_source"])
        assertEquals("interstitial", params["ad_format"])
        assertEquals("ca-app-pub-xxx/yyy", params["ad_unit_name"])
        assertEquals(1.5, params["value"])
        assertEquals("USD", params["currency"])
    }
}
