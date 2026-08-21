# Tích hợp `com.pirago.ads-helper` vào app đích

File này dành cho **AI (hoặc người) đang làm việc trong repo app đích**, không phải repo SDK. Thực hiện tuần tự. Không bịa ad unit ID. Không dùng `com.ads.sdk.TestAdUnits` trên bản production.

## 0. Trước khi sửa code

1. Xác định app là **XML** hay **Compose** (hoặc cả hai).
2. App dùng **Koin**, không Hilt. Gọi `AdsSdk.init` trong `Application.onCreate` cạnh `startKoin`.
3. Hỏi người dùng nếu thiếu:
   - AdMob **App ID** (`ca-app-pub-…~…`)
   - Ad unit ID: banner, native, interstitial, rewarded, app open
   - Version SDK đã publish (mặc định dưới đây: `1.0.1`)
4. `minSdk` app ≥ **24**.

## 1. Gradle — repository + dependency

`settings.gradle.kts` (hoặc `dependencyResolutionManagement` của project):

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://ka1toz.github.io/pi-ads-helper/") }
    }
}
```

`build.gradle.kts` **module app**:

```kotlin
dependencies {
    implementation("com.pirago.ads-helper:sdk:1.0.1")
    // Chỉ thêm dòng này nếu màn hình dùng Jetpack Compose:
    implementation("com.pirago.ads-helper:sdk-compose:1.0.1")
}
```

Sync Gradle. Nếu resolve fail: kiểm tra mạng, URL Pages, và version đã có trên

`https://ka1toz.github.io/pi-ads-helper/com/pirago/ads-helper/sdk/maven-metadata.xml`

Không copy file `.aar` vào `libs/` trừ khi người dùng yêu cầu.

## 2. Manifest — AdMob App ID

Trong `AndroidManifest.xml` của **app** (không phải library):

```xml
<application …>
    <meta-data
        android:name="com.google.android.gms.ads.APPLICATION_ID"
        android:value="ca-app-pub-xxxxxxxx~yyyyyyyyyy" />
</application>
```

Dùng App ID **của app này**. Debug có thể dùng sample Google `ca-app-pub-3940256099942544~3347511713` nếu người dùng đồng ý.

Nếu `RemoteConfigPolicy.FetchAndRead` hoặc `ReadOnly`: app phải có `google-services.json` + plugin `com.google.gms.google-services`.

## 3. Ad unit IDs — object của app

Tạo file trong package app, ví dụ `AdUnits.kt`. Thay chuỗi bằng ID AdMob thật:

```kotlin
object AdUnits {
    const val BANNER = "ca-app-pub-xxxx/yyyy"
    const val NATIVE = "ca-app-pub-xxxx/yyyy"
    const val INTERSTITIAL = "ca-app-pub-xxxx/yyyy"
    const val REWARDED = "ca-app-pub-xxxx/yyyy"
    const val APP_OPEN = "ca-app-pub-xxxx/yyyy"
}
```

Truyền các hằng này vào `AdsConfig` / `BannerConfig` / `loadAndBind`. Không import `TestAdUnits` cho release.

## 4. Init (Koin)

Trong `Application` hiện có (class đã `startKoin`). **Không** đổi sang Hilt. **Không** bắt buộc extend `AdsApplication`.

```kotlin
import android.app.Application
import com.ads.sdk.AdsConfig
import com.ads.sdk.AdsSdk
import com.ads.sdk.OpenAdsConfig
import com.ads.sdk.RemoteConfigPolicy
import com.ads.sdk.ResumeFormat
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@App)
            modules(appModule) // module Koin sẵn có của app
        }
        AdsSdk.init(
            this,
            AdsConfig(
                debug = BuildConfig.DEBUG,
                debugConsentEea = false,
                interstitialIntervalSec = 15,
                interstitialIntervalRemoteKey = "interval_show_interstitial",
                enableResumeAds = true,
                resumeRemoteKey = "show_resum_ads",
                resumeAdUnitId = AdUnits.APP_OPEN,
                resumeFormat = ResumeFormat.AppOpen,
                openAds = OpenAdsConfig(
                    appOpenAdUnitId = AdUnits.APP_OPEN,
                    interstitialAdUnitId = AdUnits.INTERSTITIAL,
                ),
                remote = RemoteConfigPolicy.None, // hoặc ReadOnly / FetchAndRead
            ),
        )
    }
}
```

Ghi `Application` này trong manifest (`android:name`).

`AdsConfig.resumeAdUnitId` và `OpenAdsConfig.*AdUnitId` **phải** gán ID app — default SDK là test Google.

## 5. Splash / consent / open ads

Splash Activity, sau `setContentView` / `setContent`:

```kotlin
AdsSdk.appOpen.disableResumeWith(SplashActivity::class.java)

AdsSdk.consent.obtainAndShow(this) {
    AdsSdk.native.preload(applicationContext, "home_collap", AdUnits.NATIVE)
    AdsSdk.native.preload(applicationContext, "home_collap_collapsed", AdUnits.NATIVE)
    AdsSdk.openAds.showIfEligible(this, object : AdCallback {
        override fun onNextAction() {
            // luôn chạy: show, fail, skip, timeout — điều hướng vào Home tại đây
        }
    })
}
```

- Open ads lần đầu: RC `show_open_ads` **và** `show_open_ads_first_open`. `show_opens_ads_type` true = Inter, false = App Open. Không ăn `ads_interval`.
- Interstitial cổ điển: `AdsSdk.splash.load(activity, AdUnits.INTERSTITIAL, timeoutMs, callback)`.
- `onNextAction()` **luôn** gọi — không chặn navigation nếu ad fail.

## 6. XML — gắn vào `ViewGroup`

Layout: một `FrameLayout` trống làm host (ví dụ `@+id/frBottom`).

```kotlin
// Banner
AdsSdk.banner.load(
    activity,
    binding.frBanner,
    shimmer = null,
    BannerConfig(AdUnits.BANNER, BannerType.Adaptive),
)

// Native template
AdsSdk.native.loadAndBind(
    activity,
    binding.frNative,
    AdUnits.NATIVE,
    NativeTemplate.Medium,
    placement = "home",
)

// Native layout XML của app
AdsSdk.native.loadAndBind(
    activity,
    binding.frNative,
    AdUnits.NATIVE,
    R.layout.layout_native_ad_custom,
)

// Slot đáy Home (collap ↔ Native Small / Banner) — đọc RC của app
val rc = AdsSdk.remoteConfig
AdsSdk.bottom.load(
    activity,
    binding.frBottom,
    BottomAdConfig(
        showNativeSmall = rc.getBoolean("is_show_native_bottom", true),
        nativeAdUnitId = AdUnits.NATIVE,
        banner = BannerConfig(AdUnits.BANNER, BannerType.Adaptive),
        collapsible = true,
        reloadSec = rc.getLong("time_reload_collap_ad", 15L).toInt(),
    ),
    placement = "home_collap",
)

// Interstitial (content)
AdsSdk.interstitial.loadAndShow(
    activity,
    AdUnits.INTERSTITIAL,
    ignoreInterval = false, // true = bỏ qua ads_interval (back-home)
    callback,
)

// Rewarded
AdsSdk.rewarded.load(activity, AdUnits.REWARDED, object : AdCallback {
    override fun onNextAction() = Unit
    override fun onAdLoaded() {
        AdsSdk.rewarded.show(activity, callback)
    }
})
```

`onDestroy` / rời màn: `AdsSdk.banner.destroy(container)` và/hoặc `AdsSdk.native.destroy(container)` nếu Activity tự quản lý container.

### Native custom XML (Pirago / Themie compatible)

Root là `NativeAdView`, hoặc wrapper chứa `NativeAdView` (`nativeAdView` / `ads_sdk_native_ad_view` / `native_ad_view`). Headline bắt buộc. CTA có thể là `TextView`/`Button` **hoặc** `FrameLayout` + nested text.

| Asset | ID (một trong các tên) |
|---|---|
| Headline | `ads_sdk_headline` / `adHeadline` / `ad_headline` / `native_ad_headline` |
| Body | `ads_sdk_body` / `adBody` / `ad_body` / `native_ad_body` |
| CTA | `ads_sdk_cta` / `adCallToAction` / `ad_call_to_action` / `native_ad_call_to_action` |
| CTA label (CTA là ViewGroup) | `native_ad_call_to_action_text` |
| Icon | `ads_sdk_icon` / `adIcon` / `ad_icon` / `native_ad_icon` / `adAppIcon` |
| Media | `ads_sdk_media` / `adMedia` / `ad_media` / `native_ad_media` |
| Advertiser | `ads_sdk_advertiser` / `adAdvertiser` |
| Stars | `ads_sdk_stars` / `adStarRating` / `ad_stars` |
| AdChoices | `ads_sdk_ad_choices` / `ad_choices_container` / `ad_choices_view` |

Sau bind: `native_ad_content_root` → `VISIBLE` (kể cả XML `invisible`), `native_loading_root` → `GONE`.

#### Map layout Themie → API

| Layout app | Cách gọi |
|---|---|
| `layout_native_ad_medium` | `AdsSdk.native.loadAndBind(activity, container, AdUnits.NATIVE, R.layout.layout_native_ad_medium, placement = "…")` |
| `layout_native_ad_small` | `loadAndBind(..., R.layout.layout_native_ad_small)` |
| `layout_native_medium_language` | `loadAndBind(..., R.layout.layout_native_medium_language)` |
| `layout_native_ad_fullscreen` | `loadAndBind(..., R.layout.layout_native_ad_fullscreen)` — bind `adHeadline` / `adAppIcon` / `adCallToAction`. **`btnCloseAd` countdown vẫn do app** (ngoài `NativeAdView`) |
| `layout_native_ad_collapsible` | Chỉ làm **expanded** XML: `expandedLayoutRes = R.layout.layout_native_ad_collapsible` (hoặc medium). **Không** thay chrome SDK |

```kotlin
// In-layout medium (drop-in, không rename ID)
AdsSdk.native.loadAndBind(
    activity,
    binding.frNative,
    AdUnits.NATIVE,
    R.layout.layout_native_ad_medium,
    placement = "get_theme",
)

// Home bottom: chrome SDK + medium/small của app
AdsSdk.bottom.load(
    activity,
    binding.frBottom,
    BottomAdConfig(
        showNativeSmall = true,
        nativeAdUnitId = AdUnits.NATIVE,
        banner = BannerConfig(AdUnits.BANNER, BannerType.Adaptive),
        collapsible = true,
        reloadSec = 15,
        expandedLayoutRes = R.layout.layout_native_ad_medium,
        collapsedLayoutRes = R.layout.layout_native_ad_small,
    ),
    placement = "home_collap",
)
```

Compose:

```kotlin
AdsNative(adUnitId = AdUnits.NATIVE, layoutRes = R.layout.layout_native_ad_medium)
AdsBottom(
    BottomAdConfig(
        showNativeSmall = true,
        nativeAdUnitId = AdUnits.NATIVE,
        banner = BannerConfig(AdUnits.BANNER, BannerType.Adaptive),
        collapsible = true,
        reloadSec = 15,
        expandedLayoutRes = R.layout.layout_native_ad_medium,
        collapsedLayoutRes = R.layout.layout_native_ad_small,
    ),
    placement = "home_collap",
)
```

**App vẫn tự làm:** blur `adMediaBackground`, badge `AD`, `btnCloseAd` countdown, unhide nếu tắt optional chrome. **Không** custom XML cho banner / inter / rewarded / AOA.

Khuyến nghị fullscreen Themie: gắn `@+id/ad_choices_view` lên `AdChoicesView` (hiện không có id — GMA vẫn có thể overlay).

## 7. Compose

Dependency `sdk-compose`. ViewModel **không** giữ `Activity` / `View`. State = bật/tắt slot; Effect = show inter / rewarded.

```kotlin
import com.ads.sdk.compose.AdsBanner
import com.ads.sdk.compose.AdsBottom
import com.ads.sdk.compose.AdsNative
import com.ads.sdk.compose.AdsNativeCollapsible

if (state.showHomeBottom) {
    AdsBottom(config = state.homeBottom, placement = "home_collap")
}
if (state.showNative) {
    AdsNative(adUnitId = AdUnits.NATIVE, template = NativeTemplate.Medium, placement = "home")
}
AdsNative(adUnitId = AdUnits.NATIVE, layoutRes = R.layout.layout_native_ad_custom)
AdsBanner(BannerConfig(AdUnits.BANNER, BannerType.Adaptive))
```

`LaunchedEffect` collect effect → `AdsSdk.interstitial.loadAndShow` / `AdsSdk.rewarded.show` với `LocalActivity.current`. `onNextAction()` đưa về ViewModel để navigate.

Preview: không gọi GMA; dùng placeholder.

## 8. Remote Config (app sở hữu flag màn hình)

SDK **không** đọc `is_show_inter_*` / `is_load_native_*`. App đọc rồi mới gọi API.

| Policy | Khi nào dùng |
|---|---|
| `None` | App chưa gắn Firebase RC |
| `ReadOnly` | App đã `fetchAndActivate` trước `AdsSdk.init` |
| `FetchAndRead` | SDK fetch hộ; **không** fetch thêm lần nữa ở Splash |

```kotlin
val rc = AdsSdk.remoteConfig
if (rc.getBoolean("is_show_inter_back_home", false)) {
    AdsSdk.interstitial.loadAndShow(activity, AdUnits.INTERSTITIAL, ignoreInterval = true, callback)
}
```

Key SDK tự đọc (nếu có trên Firebase): interval interstitial, `show_resum_ads`, open-ads flags, `time_reload_collap_ad`. Tên key truyền qua `AdsConfig`.

Không `FetchAndRead` đồng thời Splash `fetchAndActivate` trừ khi chỉ một bên owner.

## 9. Quy tắc interval

- Interstitial **content**: chờ `ads_interval` giây sau lần dismiss content trước. `ignoreInterval = true` bỏ chờ (back-home).
- Open-as-inter và resume-as-inter **không** dùng gate đó.

## 10. ProGuard

AAR đã kèm `consumer-rules.pro`. Không copy rule trừ khi minify vẫn strip GMA. Không tắt R8 trên app vì SDK.

## 11. Checklist AI phải verify

- [ ] `maven { url = uri("https://ka1toz.github.io/pi-ads-helper/") }` trong settings
- [ ] `implementation("com.pirago.ads-helper:sdk:…")` (và `sdk-compose` nếu Compose)
- [ ] `APPLICATION_ID` trên manifest
- [ ] `AdsSdk.init` trong `Application` + Koin; ID production trên `AdsConfig`
- [ ] Splash: consent → preload native (nếu Home collap) → `openAds.showIfEligible` / splash inter; `onNextAction` navigate
- [ ] Slot in-layout: `FrameLayout` host; Compose không giữ View trong ViewModel
- [ ] Không `TestAdUnits` trên flavor release
- [ ] Resume disable trên Splash: `AdsSdk.appOpen.disableResumeWith(SplashActivity::class.java)`

## Việc AI không được làm

- Đổi group Maven / URL Pages
- Hard-code test ID Google vào `release`
- Inject `Activity` vào Koin graph để giữ lâu hơn lifecycle màn
- Fetch Firebase RC hai lần (SDK + Splash) khi `FetchAndRead`
- Sửa source `:ads-sdk` trong app đích — app chỉ *consume* artifact
