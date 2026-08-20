package com.ads.sdk.revenue

import com.ads.sdk.PaidAdEvent
import com.google.android.gms.ads.AdValue
import com.google.android.gms.ads.ResponseInfo

internal object PaidEventMapper {
    fun map(
        adValue: AdValue,
        responseInfo: ResponseInfo?,
        adFormat: String,
        adUnitId: String,
    ): PaidAdEvent {
        val adapter = responseInfo?.loadedAdapterResponseInfo
        return PaidAdEvent(
            valueMicros = adValue.valueMicros,
            currencyCode = adValue.currencyCode,
            precision = adValue.precisionType,
            adSource = adapter?.adSourceName,
            adFormat = adFormat,
            adUnitId = adUnitId,
        )
    }

    fun toFirebaseParams(event: PaidAdEvent): Map<String, Any> {
        val value = event.valueMicros / 1_000_000.0
        return mapOf(
            "ad_platform" to event.adPlatform,
            "ad_source" to (event.adSource ?: "AdMob"),
            "ad_format" to event.adFormat,
            "ad_unit_name" to event.adUnitId,
            "value" to value,
            "currency" to event.currencyCode,
        )
    }
}
