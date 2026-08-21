package com.ads.sdk.nativead

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import com.ads.sdk.R
import com.ads.sdk.internal.SdkLog
import com.google.android.gms.ads.nativead.AdChoicesView
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView

/**
 * Maps a host XML layout onto [NativeAdView] asset slots.
 *
 * Canonical IDs are `ads_sdk_*`. Aliases: DIY (`adHeadline`), Google sample
 * (`ad_headline`), Pirago/Themie (`native_ad_*`, `adAppIcon`).
 */
internal object NativeAssetBinder {

    fun findNativeAdView(root: View): NativeAdView? {
        if (root is NativeAdView) return root
        root.findViewById<NativeAdView>(R.id.ads_sdk_native_ad_view)?.let { return it }
        root.findViewById<NativeAdView>(R.id.nativeAdView)?.let { return it }
        root.findViewById<NativeAdView>(R.id.native_ad_view)?.let { return it }
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                findNativeAdView(root.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    fun populate(adView: NativeAdView, nativeAd: NativeAd) {
        val headline = adView.findAsset<TextView>(
            R.id.ads_sdk_headline,
            R.id.adHeadline,
            R.id.ad_headline,
            R.id.native_ad_headline,
        )
        val body = adView.findAsset<TextView>(
            R.id.ads_sdk_body,
            R.id.adBody,
            R.id.ad_body,
            R.id.native_ad_body,
        )
        val cta = adView.findAsset<View>(
            R.id.ads_sdk_cta,
            R.id.adCallToAction,
            R.id.ad_call_to_action,
            R.id.native_ad_call_to_action,
        )
        val icon = adView.findAsset<ImageView>(
            R.id.ads_sdk_icon,
            R.id.adIcon,
            R.id.ad_icon,
            R.id.native_ad_icon,
            R.id.adAppIcon,
        )
        val media = adView.findAsset<MediaView>(
            R.id.ads_sdk_media,
            R.id.adMedia,
            R.id.ad_media,
            R.id.native_ad_media,
        )
        val advertiser = adView.findAsset<TextView>(
            R.id.ads_sdk_advertiser,
            R.id.adAdvertiser,
            R.id.ad_advertiser,
        )
        val stars = adView.findAsset<RatingBar>(
            R.id.ads_sdk_stars,
            R.id.adStarRating,
            R.id.starRatingView,
            R.id.ad_stars,
        )
        val choices = adView.findAsset<AdChoicesView>(
            R.id.ads_sdk_ad_choices,
            R.id.ad_choices_container,
            R.id.ad_choices_view,
        )

        adView.headlineView = headline
        adView.bodyView = body
        adView.callToActionView = cta
        adView.iconView = icon
        adView.mediaView = media
        adView.advertiserView = advertiser
        adView.starRatingView = stars
        adView.adChoicesView = choices

        if (headline == null) {
            SdkLog.w("Native bind missing headline view — ad will not display")
        }
        headline?.text = nativeAd.headline

        body?.apply {
            text = nativeAd.body
            visibility = if (nativeAd.body.isNullOrBlank()) View.GONE else View.VISIBLE
        }
        applyCallToAction(cta, nativeAd.callToAction)
        val iconDrawable = nativeAd.icon?.drawable
        if (iconDrawable != null) {
            icon?.setImageDrawable(iconDrawable)
            icon?.visibility = View.VISIBLE
        } else {
            icon?.visibility = View.GONE
        }
        advertiser?.apply {
            text = nativeAd.advertiser
            visibility = if (nativeAd.advertiser.isNullOrBlank()) View.GONE else View.VISIBLE
        }
        stars?.apply {
            val rating = nativeAd.starRating
            if (rating != null) {
                this.rating = rating.toFloat()
                visibility = View.VISIBLE
            } else {
                visibility = View.GONE
            }
        }
        if (media != null) {
            val content = nativeAd.mediaContent
            if (content != null) {
                media.mediaContent = content
                media.visibility = View.VISIBLE
            } else {
                media.visibility = View.GONE
            }
        }
        revealHostChrome(adView)
    }

    private fun applyCallToAction(cta: View?, text: String?) {
        if (cta == null) return
        val visible = if (text.isNullOrBlank()) View.GONE else View.VISIBLE
        when (cta) {
            is TextView -> {
                cta.text = text
                cta.visibility = visible
            }
            is ViewGroup -> {
                val label = cta.findAsset<TextView>(
                    R.id.native_ad_call_to_action_text,
                    R.id.ads_sdk_cta,
                ) ?: firstTextView(cta)
                label?.text = text
                cta.visibility = visible
            }
        }
    }

    private fun revealHostChrome(start: View) {
        var node: View? = start
        repeat(8) {
            val current = node ?: return
            current.findViewById<View>(R.id.native_ad_content_root)?.visibility = View.VISIBLE
            current.findViewById<View>(R.id.native_loading_root)?.visibility = View.GONE
            node = current.parent as? View
        }
    }

    private fun firstTextView(group: ViewGroup): TextView? {
        for (i in 0 until group.childCount) {
            when (val child = group.getChildAt(i)) {
                is TextView -> return child
                is ViewGroup -> firstTextView(child)?.let { return it }
            }
        }
        return null
    }

    private inline fun <reified T : View> View.findAsset(vararg ids: Int): T? {
        for (id in ids) {
            findViewById<View>(id)?.let { if (it is T) return it }
        }
        return null
    }
}
