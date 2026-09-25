# Tích hợp `com.pirago.ads-helper` vào app đích

File này dành cho **AI (hoặc người) đang làm việc trong repo app đích**, không phải repo SDK. Thực hiện tuần tự. Không bịa ad unit ID. Không dùng `com.ads.sdk.TestAdUnits` trên bản production.

**Phương án mediator (đã chốt, đã có trong SDK):** APK có thể chứa **cả GMA và MAX**. Firebase `mediation_type` chọn **một** engine mỗi session. MAX chỉ có **4 ad unit**: Inter, AOA, Banner, MREC. Native / rewarded / native-after-inter chỉ chạy trên nhánh AdMob.

| Artifact | Vai trò | Ghi chú |
|---|---|---|
| `sdk` | API public, GMA owned | App **luôn** thêm |
| `sdk-compose` | `AndroidView` helpers | Chỉ khi app Compose |
| `sdk-max` | Cầu MAX (`AppLovinSdk`) | Chỉ khi app hỗ trợ `mediation_type = 0` |

Contract file này = **1.0.4** (`sdk` + `sdk-compose` + `sdk-max`). `gradle.properties` trên source đã là 1.0.4. Kiểm tra metadata Pages có 1.0.4 trước khi sync app.

**1.0.4:** thêm `AdsSdk.openAdInspector`, cổng `AdsConfig.isDebuggableAds` (dev true, product false). AdMob mở Ad Inspector; MAX mở Mediation Debugger. Xem mục 5.3.

## 0. Trước khi sửa code

1. Xác định app là **XML** hay **Compose** (hoặc cả hai).
2. App dùng **Koin**, không Hilt. `startKoin` trong `Application.onCreate`. **Không** start engine ads (GMA `MobileAds.initialize` / MAX `AppLovinSdk.initialize`) trước khi có `mediation_type`.
3. Hỏi người dùng nếu thiếu:
   - AdMob **App ID** (`ca-app-pub-…~…`)
   - Ad unit **AdMob**: banner, native, interstitial, rewarded, app open
   - AppLovin **SDK Key** (nếu app hỗ trợ MAX)
   - Ad unit **MAX** (đúng 4): Inter, AOA, Banner, MREC — không bịa native/rewarded MAX
   - Version đã publish (mặc định dưới đây: **1.0.4**; kiểm tra metadata Pages trước khi sync)
4. `minSdk` app ≥ **24**.
5. App kids / user là trẻ em: **không** init MAX (AppLovin cấm). Haircut-like = audience chung thì OK.
6. Họ Remote Config: **Pirago/Haircut** (chuỗi `aoa_type` / `resume_type`) **khác** Led Banner (boolean `show_open_ads` / `show_resum_ads`). Gán đúng field `AdsConfig` — xem mục 5. **Không** gán chuỗi `resume_type` vào `resumeRemoteKey` (field đó là boolean).

## 1. Phương án mediator (đọc trước khi viết code)

### Luật

| Luật | Chi tiết |
|---|---|
| Một session = một mediator | `0` = MAX, `1` = AdMob Mediation. Không init cả hai auction trong cùng process/session |
| Chọn lúc nào | Sau `fetchAndActivate` RC (Splash). Fetch fail → default **`1`** (AdMob) |
| Đổi RC giữa chừng | Chỉ có hiệu lực **lần mở app sau** |
| GMA SDK | **Luôn giữ** trong app (`play-services-ads`). Nhánh MAX vẫn cần GMA vì Google là adapter |
| AdMob demand | **Giữ**. Nhánh `1`: Google cầm auction (+ adapter app thêm). Nhánh `0`: Google là mạng trong waterfall MAX |
| AdMob Mediation vs MAX | Không chạy **cùng lúc**. Adapter AdMob Mediation (`com.google.ads.mediation:*`) chỉ dùng khi session = `1` |
| MAX ad unit | **Chỉ 4 key.** Không có native MAX, không có rewarded MAX |
| Adapter mạng | **App thêm** (Gradle). SDK không pin Meta/Mintegral/ironSource/… |
| `sdk-max` | Pin `com.applovin:applovin-sdk`. App **không** thêm lại core AppLovin; chỉ thêm **adapter mạng** MAX |

`mediation_type` (Firebase, number, default `1`):

- `0` — MAX
- `1` — AdMob Mediation (GMA)

### Format theo nhánh

Cùng một `FrameLayout` host. App đọc `AdsSdk.isMax` (hoặc `mediation_type`) rồi gọi API tương ứng.

| Slot / format | AdMob (`1`) | MAX (`0`) |
|---|---|---|
| Interstitial (mọi `is_show_inter_*`), open/resume dạng **inter** | GMA Inter | MAX **Inter** |
| Open / resume dạng **aoa** | GMA App Open | MAX **AOA** |
| Banner | GMA Banner | MAX **Banner** |
| Native medium / small / collap / full-sau-inter | GMA Native | **Không load** |
| MREC (Language, Onboarding, Settings, Uninstall, Home, Item, Gameplay, …) | **Không load** | MAX **MREC** |
| Rewarded | GMA Rewarded | **Không có** |

Native after inter (`is_show_native_after_inter`): **bỏ qua khi MAX**.

### ID sau khi engine ready

App **không** tự if/else chuỗi ID rải trong Activity nếu SDK đã resolve:

```kotlin
AdsSdk.units.interstitial  // GMA inter hoặc MAX Inter
AdsSdk.units.appOpen       // GMA AOA hoặc MAX AOA
AdsSdk.units.banner        // GMA banner hoặc MAX Banner
AdsSdk.units.mrec          // MAX MREC; rỗng khi AdMob
AdsSdk.units.native        // GMA native; rỗng khi MAX
AdsSdk.units.rewarded      // GMA rewarded; rỗng khi MAX
```

Truyền `AdsSdk.units.*` vào `load` / `loadAndBind` / `loadAndShow`. Không hard-code `ca-app-pub` hay MAX ID tại chỗ gọi.

## 2. Gradle — repository + dependency

`settings.gradle.kts` (hoặc `dependencyResolutionManagement` của project):

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://ka1toz.github.io/pi-ads-helper/") }
        // Chỉ khi app hỗ trợ MAX + adapter mạng MAX:
        maven { url = uri("https://artifacts.applovin.com/android") }
        maven { url = uri("https://android-sdk.is.com") }
        maven { url = uri("https://dl-maven-android.mintegral.com/repository/mbridge_android_sdk_oversea") }
        maven { url = uri("https://artifact.bytedance.com/repository/pangle") }
    }
}
```

Chỉ thêm Maven mạng khi app **thật sự** khai adapter đó. Không copy nguyên list nếu không dùng.

`build.gradle.kts` **module app**:

```kotlin
dependencies {
    implementation("com.pirago.ads-helper:sdk:1.0.4")
    // Compose:
    implementation("com.pirago.ads-helper:sdk-compose:1.0.4")
    // Bắt buộc nếu app hỗ trợ mediation_type = 0:
    implementation("com.pirago.ads-helper:sdk-max:1.0.4")

    // AdMob Mediation adapters — app tự thêm, dùng khi session = 1
    // implementation("com.google.ads.mediation:facebook:…")

    // MAX network adapters — app tự thêm, dùng khi session = 0.
    // Không thêm lại com.applovin:applovin-sdk (sdk-max đã pin).
    // implementation("com.applovin.mediation:google-adapter:…")
    // implementation("com.applovin.mediation:facebook-adapter:…")
}
```

Sync Gradle. Nếu resolve fail: kiểm tra mạng, URL Pages, và version đã có trên

`https://ka1toz.github.io/pi-ads-helper/com/pirago/ads-helper/sdk/maven-metadata.xml`

và (khi dùng MAX):

`https://ka1toz.github.io/pi-ads-helper/com/pirago/ads-helper/sdk-max/maven-metadata.xml`

Không copy file `.aar` vào `libs/` trừ khi người dùng yêu cầu. **Không** bọc AAR JBase (`maxads-release.aar`, `UnityPlayer`).

Cùng module app, thêm flag build cho Ad Inspector. Dev = `true`, product = `false`. Đặt trên **build type** hoặc **flavor** tùy app đang tách môi trường thế nào — không hard-code `true` trong `release` / product.

```kotlin
android {
    buildFeatures { buildConfig = true }
    buildTypes {
        getByName("debug") {
            buildConfigField("boolean", "IS_DEBUGGABLE_ADS", "true")
        }
        getByName("release") {
            buildConfigField("boolean", "IS_DEBUGGABLE_ADS", "false")
        }
    }
}
```

## 3. Manifest — AdMob App ID + MAX SDK Key

Trong `AndroidManifest.xml` của **app** (không phải library):

```xml
<application …>
    <meta-data
        android:name="com.google.android.gms.ads.APPLICATION_ID"
        android:value="ca-app-pub-xxxxxxxx~yyyyyyyyyy" />
    <!-- Bắt buộc nếu app hỗ trợ MAX. Key từ AppLovin dashboard → Account > Keys -->
    <meta-data
        android:name="applovin.sdk.key"
        android:value="${applovinSdkKey}" />
</application>
```

AdMob App ID **luôn** khai (cả nhánh MAX — adapter Google cần). Debug có thể dùng sample Google `ca-app-pub-3940256099942544~3347511713` nếu người dùng đồng ý.

Nếu `RemoteConfigPolicy.FetchAndRead` hoặc `ReadOnly`: app phải có `google-services.json` + plugin `com.google.gms.google-services`.

## 4. Ad unit IDs — hai catalog của app

Tạo file trong package app, ví dụ `AdUnits.kt`. Không import `TestAdUnits` cho release.

```kotlin
object AdUnits {
    object Admob {
        const val BANNER = "ca-app-pub-xxxx/yyyy"
        const val NATIVE = "ca-app-pub-xxxx/yyyy"
        const val INTERSTITIAL = "ca-app-pub-xxxx/yyyy"
        const val REWARDED = "ca-app-pub-xxxx/yyyy"
        const val APP_OPEN = "ca-app-pub-xxxx/yyyy"
    }

    /** MAX: đúng 4 key. Không thêm native / rewarded. */
    object Max {
        const val INTER = "…"
        const val AOA = "…"
        const val BANNER = "…"
        const val MREC = "…"
    }
}
```

Truyền cả hai bộ vào `AdsConfig` lúc init (`admob` + `max`). SDK chọn bộ nào sau `mediation_type`. Thiếu bộ MAX / thiếu `sdk-max` mà RC = `0` → không request ads MAX; log lỗi, `onNextAction()` vẫn chạy.

## 5. Init (Koin) + thứ tự RC

Trong `Application` hiện có. **Không** đổi sang Hilt. **Không** bắt buộc extend `AdsApplication`.

### 5.1 Field RC — đừng gán nhầm

SDK **default** vẫn là họ Led Banner (boolean). App Pirago/Haircut **phải** override.

| Firebase key | Kiểu | Field `AdsConfig` đúng | **Sai** (sẽ gãy) |
|---|---|---|---|
| `ads_interval` | number | `interstitialIntervalRemoteKey` | — |
| `aoa_type` | string `inter` \| `aoa` \| rỗng | `openAds.formatRemoteKey` | `enabledRemoteKey` / `typeIsInterRemoteKey` (boolean) |
| `show_aoa_first_open` | boolean | `openAds.firstOpenRemoteKey` | — |
| `resume_type` | string `inter` \| `aoa` \| rỗng | **`resumeTypeRemoteKey`** | `resumeRemoteKey` (boolean `show_resum_ads`) |
| `resume_ads_interval` | number | `resumeAdsIntervalRemoteKey` | `interstitialIntervalRemoteKey` |
| `mediation_type` | number `0`/`1` | `mediationRemoteKey` | — |
| `show_open_ads` / `show_opens_ads_type` / `show_resum_ads` | boolean | Chỉ app **Led Banner** (`enabledRemoteKey`, `typeIsInterRemoteKey`, `resumeRemoteKey`) | App Haircut **không** dùng bộ này |

Rỗng trên `aoa_type` / `resume_type` = **tắt** format đó (`OpenAdFormat.Off`).

### 5.2 Mẫu Pirago / Haircut

```kotlin
import android.app.Application
import com.ads.sdk.AdmobAdUnits
import com.ads.sdk.AdsConfig
import com.ads.sdk.AdsSdk
import com.ads.sdk.MaxAdUnits
import com.ads.sdk.OpenAdFormat
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
            modules(appModule)
        }
        AdsSdk.init(
            this,
            AdsConfig(
                debug = BuildConfig.DEBUG,
                isDebuggableAds = BuildConfig.IS_DEBUGGABLE_ADS,
                debugConsentEea = false,
                interstitialIntervalSec = 10,
                interstitialIntervalRemoteKey = "ads_interval",
                enableResumeAds = true,
                resumeTypeRemoteKey = "resume_type",
                resumeTypeDefault = OpenAdFormat.Inter,
                resumeAdsIntervalRemoteKey = "resume_ads_interval",
                resumeAdUnitId = AdUnits.Admob.APP_OPEN,
                resumeFormat = ResumeFormat.Interstitial,
                openAds = OpenAdsConfig(
                    formatRemoteKey = "aoa_type",
                    firstOpenRemoteKey = "show_aoa_first_open",
                    appOpenAdUnitId = AdUnits.Admob.APP_OPEN,
                    interstitialAdUnitId = AdUnits.Admob.INTERSTITIAL,
                    firstOpenDefault = false,
                    formatDefault = OpenAdFormat.Off,
                ),
                admob = AdmobAdUnits(
                    banner = AdUnits.Admob.BANNER,
                    native = AdUnits.Admob.NATIVE,
                    interstitial = AdUnits.Admob.INTERSTITIAL,
                    rewarded = AdUnits.Admob.REWARDED,
                    appOpen = AdUnits.Admob.APP_OPEN,
                ),
                max = MaxAdUnits(
                    interstitial = AdUnits.Max.INTER,
                    appOpen = AdUnits.Max.AOA,
                    banner = AdUnits.Max.BANNER,
                    mrec = AdUnits.Max.MREC,
                ),
                mediationRemoteKey = "mediation_type",
                remote = RemoteConfigPolicy.FetchAndRead, // hoặc ReadOnly nếu Splash fetch
            ),
        )
    }
}
```

Ghi `Application` này trong manifest (`android:name`).

`AdsConfig.resumeAdUnitId` và `OpenAdsConfig.*AdUnitId` **phải** gán ID app — default SDK là test Google. Catalog `admob` / `max` là nguồn `AdsSdk.units` sau khi RC chọn engine.

App **không** hỗ trợ MAX: bỏ `sdk-max`, bỏ `max = MaxAdUnits(...)`, `mediation_type` sẽ luôn về AdMob (default `1`). Vẫn truyền `admob = AdmobAdUnits(...)`.

### 5.3 Ad Inspector / Mediation Debugger (chỉ bản dev)

`AdsConfig.isDebuggableAds` là flag build của app (mục 2), không phải Remote Config. `AdsSdk.openAdInspector` chỉ mở khi flag đó là `true`. Product (`false`) thì no-op.

Cùng một lời gọi, SDK chọn màn theo session:

- AdMob: Ad Inspector (`MobileAds.openAdInspector`). Có callback đóng.
- MAX: Mediation Debugger (`AppLovinSdk.showMediationDebugger`), sau khi MAX init xong. Không có callback đóng. Cần `sdk-max` trên classpath; thiếu thì no-op.

Gọi tay từ `Activity` đang foreground, sau khi đã load placement đang No Fill, để log request có bản ghi đó. Không gọi lúc splash, không gọi trong `onAdFailedToLoad`.

Nút hoặc cử chỉ chỉ hiện khi `AdsSdk.isDebuggableAds`. Bản product không có lối vào. App không gọi `showMediationDebugger` trực tiếp.

```kotlin
if (AdsSdk.isDebuggableAds) {
    AdsSdk.openAdInspector(activity) { error ->
        // AdMob: error == null khi đóng bình thường. MAX: callback không chạy.
    }
}
```

Ad Inspector cho biết request nào No Fill, message, và adapter mediation Ready hay không. Single ad source test lưu trên máy cho đến khi tắt trong Inspector — tắt nếu máy test đang kẹt một network (ví dụ Meta) và không waterfall.

Mediation Debugger cho biết adapter MAX Ready hay thiếu, và waterfall của ad unit Inter, AOA, Banner, MREC. Nó không có nhật ký từng request No Fill như Ad Inspector.

### Thứ tự bắt buộc (Splash)

1. `AdsSdk.appOpen.disableResumeWith(SplashActivity::class.java)`
2. Fetch RC **một lần** (`FetchAndRead` **hoặc** Splash `fetchAndActivate`, không cả hai)
3. Đọc `mediation_type` (default `1`) → start **đúng một** engine
4. UMP `obtainAndShow`
5. Nếu MAX: init AppLovin **sau** UMP (TCF phải có trước). Nếu AdMob: `MobileAds.initialize` như hiện tại
6. Preload / open ads / navigate (`onNextAction` luôn chạy)

Không gọi `MobileAds.initialize` và `AppLovinSdk.initialize` trong cùng session. SDK `ensureNetworkSdk` đã chọn một nhánh; app **không** tự gọi `AppLovinSdk.initialize` thêm.

## 6. Splash / consent / open ads

Splash Activity, sau `setContentView` / `setContent`:

```kotlin
AdsSdk.appOpen.disableResumeWith(SplashActivity::class.java)

AdsSdk.consent.obtainAndShow(this) {
    if (!AdsSdk.isMax) {
        AdsSdk.native.preload(applicationContext, "home_collap", AdsSdk.units.native)
        AdsSdk.native.preload(applicationContext, "home_collap_collapsed", AdsSdk.units.native)
    }
    AdsSdk.openAds.showIfEligible(this, object : AdCallback {
        override fun onNextAction() {
            // luôn chạy: show, fail, skip, timeout — điều hướng vào Home tại đây
        }
    })
}
```

Open / resume (Pirago Haircut):

| Key | Kiểu | Ý nghĩa |
|---|---|---|
| `aoa_type` | string `inter` \| `aoa` \| rỗng | Cold start: inter / AOA / tắt. Rỗng = không show |
| `show_aoa_first_open` | boolean | Lần đầu mở: cần `true` **và** `aoa_type` khác rỗng mới show |
| `resume_type` | string `inter` \| `aoa` \| rỗng | Resume; rỗng = tắt |
| `resume_ads_interval` | number | Giãn cách hai lần resume (không dùng `ads_interval`) |
| `ads_interval` | number | Giãn cách inter **content**. Không chặn AOA / resume-as-inter |

Tên key Led Banner cũ (`show_open_ads`, `show_opens_ads_type`, `show_resum_ads`, `interval_show_interstitial`) vẫn map được qua field `*RemoteKey` trên `AdsConfig`. App Pirago/Haircut dùng bảng trên.

- Interstitial cổ điển splash: `AdsSdk.splash.load(activity, AdsSdk.units.interstitial, timeoutMs, callback)`.
- `onNextAction()` **luôn** gọi — không chặn navigation nếu ad fail.

## 7. XML — gắn vào `ViewGroup`

Layout: một `FrameLayout` trống làm host (ví dụ `@+id/frBottom`). **Cùng slot**, khác format theo nhánh:

```kotlin
val rc = AdsSdk.remoteConfig
val host = binding.frSlot

if (AdsSdk.isMax) {
    if (rc.getBoolean("is_show_mrec", true) && rc.getBoolean("is_show_mrec_language", true)) {
        AdsSdk.mrec.load(activity, host, AdsSdk.units.mrec)
    }
} else {
    if (rc.getBoolean("is_show_native_medium", true)) {
        AdsSdk.native.loadAndBind(
            activity,
            host,
            AdsSdk.units.native,
            NativeTemplate.Medium,
            placement = "language",
        )
    }
}

// Banner — cả hai nhánh (ID do units resolve)
AdsSdk.banner.load(
    activity,
    binding.frBanner,
    shimmer = null,
    BannerConfig(AdsSdk.units.banner, BannerType.Adaptive),
)

// Interstitial content
AdsSdk.interstitial.loadAndShow(
    activity,
    AdsSdk.units.interstitial,
    ignoreInterval = false, // true = bỏ ads_interval (back)
    callback,
)

// Rewarded — chỉ AdMob
if (!AdsSdk.isMax) {
    AdsSdk.rewarded.load(activity, AdsSdk.units.rewarded, object : AdCallback {
        override fun onNextAction() = Unit
        override fun onAdLoaded() {
            AdsSdk.rewarded.show(activity, callback)
        }
    })
}

// Home bottom collap — chỉ AdMob. MAX: MREC vào cùng host, không native collap.
if (!AdsSdk.isMax) {
    AdsSdk.bottom.load(
        activity,
        binding.frBottom,
        BottomAdConfig(
            showNativeSmall = rc.getBoolean("is_show_native_small", true),
            nativeAdUnitId = AdsSdk.units.native,
            banner = BannerConfig(AdsSdk.units.banner, BannerType.Adaptive),
            collapsible = rc.getBoolean("is_show_native_collap", true),
            reloadSec = rc.getLong("time_reload_native_collap", 10L).toInt(),
        ),
        placement = "home_collap",
    )
}
```

`onDestroy` / rời màn: `AdsSdk.banner.destroy(container)`, `AdsSdk.native.destroy(container)`, `AdsSdk.mrec.destroy(container)` nếu Activity tự quản lý container.

Native custom XML, Themie, `AdsBottom` — **chỉ nhánh AdMob**. Xem mục 7.1.

### 7.1 Native custom XML (Pirago / Themie) — AdMob only

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

#### Map layout Themie → API (AdMob)

| Layout app | Cách gọi |
|---|---|
| `layout_native_ad_medium` | `AdsSdk.native.loadAndBind(activity, container, AdsSdk.units.native, R.layout.layout_native_ad_medium, placement = "…")` |
| `layout_native_ad_small` | `loadAndBind(..., R.layout.layout_native_ad_small)` |
| `layout_native_medium_language` | `loadAndBind(..., R.layout.layout_native_medium_language)` |
| `layout_native_ad_fullscreen` | `loadAndBind(..., R.layout.layout_native_ad_fullscreen)` — bind `adHeadline` / `adAppIcon` / `adCallToAction`. **`btnCloseAd` countdown vẫn do app** (ngoài `NativeAdView`). SDK không có `showFullscreen` |
| `layout_native_ad_collapsible` | Chỉ làm **expanded** XML: `expandedLayoutRes = R.layout.layout_native_ad_collapsible` (hoặc medium). **Không** thay chrome SDK |

```kotlin
AdsSdk.native.loadAndBind(
    activity,
    binding.frNative,
    AdsSdk.units.native,
    R.layout.layout_native_ad_medium,
    placement = "get_theme",
)

AdsSdk.bottom.load(
    activity,
    binding.frBottom,
    BottomAdConfig(
        showNativeSmall = true,
        nativeAdUnitId = AdsSdk.units.native,
        banner = BannerConfig(AdsSdk.units.banner, BannerType.Adaptive),
        collapsible = true,
        reloadSec = 15,
        expandedLayoutRes = R.layout.layout_native_ad_medium,
        collapsedLayoutRes = R.layout.layout_native_ad_small,
    ),
    placement = "home_collap",
)
```

**App vẫn tự làm:** blur `adMediaBackground`, badge `AD`, `btnCloseAd` countdown, unhide nếu tắt optional chrome. **Không** custom XML cho banner / inter / rewarded / AOA / MREC.

Khuyến nghị fullscreen Themie: gắn `@+id/ad_choices_view` lên `AdChoicesView` (hiện không có id — GMA vẫn có thể overlay).

## 8. Compose

Dependency `sdk-compose`. ViewModel **không** giữ `Activity` / `View`. State = bật/tắt slot **và** nhánh mediator; Effect = show inter / rewarded.

```kotlin
import com.ads.sdk.compose.AdsBanner
import com.ads.sdk.compose.AdsBottom
import com.ads.sdk.compose.AdsMrec
import com.ads.sdk.compose.AdsNative
import com.ads.sdk.compose.AdsNativeCollapsible

if (AdsSdk.isMax) {
    if (state.showMrec) AdsMrec(adUnitId = AdsSdk.units.mrec)
} else {
    if (state.showHomeBottom) {
        AdsBottom(config = state.homeBottom, placement = "home_collap")
    }
    if (state.showNative) {
        AdsNative(adUnitId = AdsSdk.units.native, template = NativeTemplate.Medium, placement = "home")
    }
}
AdsBanner(BannerConfig(AdsSdk.units.banner, BannerType.Adaptive))
```

`LaunchedEffect` collect effect → `AdsSdk.interstitial.loadAndShow` với `AdsSdk.units.interstitial` + `LocalActivity.current`. `onNextAction()` đưa về ViewModel để navigate. Rewarded effect chỉ emit khi `!AdsSdk.isMax`.

Preview: không gọi GMA/MAX; dùng placeholder.

## 9. Remote Config

SDK **không** đọc `is_show_inter_*` / `is_show_mrec_*` / `is_load_native_*`. App đọc rồi mới gọi API.

SDK **có** đọc (tên key qua `AdsConfig`): `mediation_type`, `ads_interval`, open/resume type, `resume_ads_interval`, reload collap.

| Policy | Khi nào dùng |
|---|---|
| `None` | App chưa gắn Firebase RC → `mediation_type` = default `1` |
| `ReadOnly` | App đã `fetchAndActivate` **trước** lúc start engine |
| `FetchAndRead` | SDK fetch hộ; **không** fetch thêm lần nữa ở Splash |

```kotlin
val rc = AdsSdk.remoteConfig
if (rc.getBoolean("is_show_inter_back", false)) {
    AdsSdk.interstitial.loadAndShow(
        activity,
        AdsSdk.units.interstitial,
        ignoreInterval = true,
        callback,
    )
}
```

Key Haircut / Pirago (app + SDK):

| Key | Ai đọc | Ghi chú |
|---|---|---|
| `mediation_type` | SDK | `0` MAX, `1` AdMob, default `1` |
| `ads_interval` | SDK | Default brief **10**. Không áp dụng AOA / resume-as-inter |
| `aoa_type` / `show_aoa_first_open` | SDK | Xem mục 6 — gán `formatRemoteKey` / `firstOpenRemoteKey` |
| `resume_type` / `resume_ads_interval` | SDK | Xem mục 6 — gán `resumeTypeRemoteKey` / `resumeAdsIntervalRemoteKey` |
| `is_show_native_medium` | App | **AdMob only** |
| `is_show_native_collap` / `is_show_native_small` / `time_reload_native_collap` | App | **AdMob only** (MAX không có native) |
| `is_show_native_after_inter` | App | Bỏ qua khi MAX |
| `is_show_mrec` + `is_show_mrec_*` / `mrec_gameplay_position` | App | **MAX only** |
| `is_show_banner` | App | Cả hai nhánh |
| `is_show_inter_*` | App | Cả hai nhánh (ID Inter theo engine) |
| `rating_popup`, `native_bg_color`, CTA/close chrome | App | Không thuộc SDK |

Không `FetchAndRead` đồng thời Splash `fetchAndActivate` trừ khi chỉ một bên owner.

## 10. Quy tắc interval

- Interstitial **content**: chờ `ads_interval` giây sau lần dismiss content trước. `ignoreInterval = true` bỏ chờ (back).
- Open-as-inter và resume-as-inter **không** dùng `ads_interval`. Resume dùng `resume_ads_interval`.

## 11. ProGuard

AAR đã kèm `consumer-rules.pro`. Không copy rule trừ khi minify vẫn strip GMA/MAX. Không tắt R8 trên app vì SDK. Adapter MAX/AdMob Mediation thường đã kèm rule trong AAR mạng.

## 12. Checklist AI phải verify

- [ ] `maven { url = uri("https://ka1toz.github.io/pi-ads-helper/") }` trong settings
- [ ] `implementation("com.pirago.ads-helper:sdk:1.0.4")` (và `sdk-compose` nếu Compose; `sdk-max` nếu hỗ trợ MAX) — chỉ sau khi metadata Pages có 1.0.4
- [ ] `APPLICATION_ID` trên manifest; `applovin.sdk.key` nếu có MAX
- [ ] Hai catalog ID (AdMob + đúng 4 MAX). Không bịa native/rewarded MAX. Truyền `admob` / `max` lúc `AdsSdk.init`
- [ ] Haircut: `formatRemoteKey = "aoa_type"`, `resumeTypeRemoteKey = "resume_type"` — **không** gán các chuỗi đó vào field boolean
- [ ] `startKoin` + `AdsSdk.init`; engine ads **sau** RC `mediation_type` (default 1)
- [ ] Một session một mediator; UMP **trước** MAX init
- [ ] Splash: consent → preload native **chỉ khi AdMob** → `openAds.showIfEligible`; `onNextAction` navigate
- [ ] Slot in-layout: AdMob = native/banner; MAX = MREC/banner; Compose không giữ View trong ViewModel
- [ ] Không `TestAdUnits` trên flavor release
- [ ] Resume disable trên Splash: `AdsSdk.appOpen.disableResumeWith(SplashActivity::class.java)`
- [ ] `IS_DEBUGGABLE_ADS`: dev `true`, product `false`. Truyền `AdsConfig.isDebuggableAds`. Nút Ad Inspector chỉ khi `AdsSdk.isDebuggableAds`; không gọi lúc splash / mỗi lần fail

## Việc AI không được làm

- Đổi group Maven / URL Pages
- Hard-code test ID Google / MAX demo vào `release`
- Inject `Activity` vào Koin graph để giữ lâu hơn lifecycle màn
- Fetch Firebase RC hai lần (SDK + Splash) khi `FetchAndRead`
- Sửa source `:ads-sdk` trong app đích — app chỉ *consume* artifact
- Init MAX và AdMob Mediation **cùng session**
- Gọi `AdsSdk.native` / `AdsSdk.bottom` / rewarded / native-after-inter khi `AdsSdk.isMax`
- Gọi MREC khi nhánh AdMob
- Thêm ad unit MAX thứ 5 (native, rewarded, …)
- Bọc AAR JBase / `UnityPlayer` / reflection `MaxAdsService`
- Dùng AppLovin với app kids hoặc user là trẻ em
- Gán `resume_type` vào `resumeRemoteKey`, hoặc `aoa_type` vào `enabledRemoteKey` / `typeIsInterRemoteKey`
- Mở Ad Inspector trên bản product, hoặc tự gọi `openAdInspector` khi load fail / lúc splash

Last edit: 2026/09/25 14:07.