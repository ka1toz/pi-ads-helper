# Ads SDK (AdMob, XML + Compose)

Android library in this repo: **`:ads-sdk`**. Optional Compose wrappers: **`:ads-sdk-compose`**. Samples: **`:demo-xml`**, **`:demo-compose`**. The original JBase AAR demo remains in **`:app`**.

v1 is Google Mobile Ads only. No UnityPlayer, no JBase AAR wrap, no MAX in the default artifact.

## Modules

| Module | Artifact | Role |
|---|---|---|
| `:ads-sdk` | `com.yourorg.ads:sdk:1.0.0-SNAPSHOT` | Public API, ViewGroup host |
| `:ads-sdk-compose` | `com.yourorg.ads:sdk-compose:1.0.0-SNAPSHOT` | `AndroidView` helpers |
| `:demo-xml` | — | ViewBinding sample |
| `:demo-compose` | — | Compose sample |
| `:app` | — | Legacy JBase reflection demo |

## Init

App `AndroidManifest.xml` (not the library):

```xml
<meta-data
    android:name="com.google.android.gms.ads.APPLICATION_ID"
    android:value="ca-app-pub-xxxxxxxx~yyyyyyyyyy" />
```

```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        AdsSdk.init(
            this,
            AdsConfig(
                debug = BuildConfig.DEBUG,
                interstitialIntervalSec = 15,
                interstitialIntervalRemoteKey = "interval_show_interstitial", // or ads_interval
                enableResumeAds = true,
                resumeFormat = ResumeFormat.AppOpen, // or Interstitial
                remote = RemoteConfigPolicy.None, // ReadOnly / FetchAndRead
                revenueLogger = LogcatRevenueLogger(),
                funnelLogger = LogcatFunnelLogger(),
            ),
        )
    }
}
```

Hilt apps cannot extend `AdsApplication` — call `AdsSdk.init` from `@HiltAndroidApp`.

Placement kill-switches (`is_show_inter_*`, `is_load_native_*`) stay in the **app**. SDK only reads global knobs (interval, open-ads flags, collap reload).

## Splash / consent / open ads

```kotlin
AdsSdk.appOpen.disableResumeWith(SplashActivity::class.java)
AdsSdk.consent.obtainAndShow(this) {
    AdsSdk.openAds.showIfEligible(this, object : AdCallback {
        override fun onNextAction() { /* navigate — always called */ }
    })
}
```

Open ads: first launch needs `show_open_ads` AND `show_open_ads_first_open`. `show_opens_ads_type` true = Inter, false = App Open. Those shows do **not** consume `ads_interval`.

Classic splash interstitial: `AdsSdk.splash.load(activity, id, timeoutMs, callback)`.

## XML banner / native

```kotlin
AdsSdk.banner.load(activity, binding.frAds, shimmer, BannerConfig(id, BannerType.Adaptive))
AdsSdk.native.loadAndBind(activity, binding.frNative, id, NativeTemplate.Medium)
AdsSdk.native.loadAndBind(activity, binding.frNative, id, R.layout.layout_native_ad_custom)
AdsSdk.bottom.load(
    activity,
    binding.frBottom,
    BottomAdConfig(
        showNativeSmall = rc.getBoolean("is_show_native_bottom", true),
        nativeAdUnitId = id,
        banner = BannerConfig(bannerId, BannerType.Adaptive),
        collapsible = true,
        reloadSec = rc.getLong("time_reload_collap_ad", 15).toInt(),
    ),
    placement = "home_collap",
)
```

Custom native XML must contain a `NativeAdView` (as root, or nested with id `nativeAdView` / `ads_sdk_native_ad_view`) and map GMA assets with these IDs:

| Asset | SDK id | DIY / Google aliases |
|---|---|---|
| Headline (required) | `ads_sdk_headline` | `adHeadline`, `ad_headline` |
| Body | `ads_sdk_body` | `adBody`, `ad_body` |
| CTA | `ads_sdk_cta` | `adCallToAction`, `ad_call_to_action` |
| Icon | `ads_sdk_icon` | `adIcon`, `ad_icon` |
| Media | `ads_sdk_media` | `adMedia`, `ad_media` |
| Advertiser | `ads_sdk_advertiser` | `adAdvertiser`, `ad_advertiser` |
| Stars | `ads_sdk_stars` | `adStarRating`, `ad_stars` |
| AdChoices | `ads_sdk_ad_choices` | `ad_choices_container` |

Layout, colors, fonts, AD badge, close/collapse buttons are **app-owned**. Banner / interstitial / AOA / rewarded UI is Google's — not customizable.

## Compose

```kotlin
AdsBanner(BannerConfig(id, BannerType.CollapsibleBottom))
AdsNative(adUnitId = id, template = NativeTemplate.Small)
AdsNative(adUnitId = id, layoutRes = R.layout.layout_native_ad_custom)
AdsBottom(homeBottomConfig, placement = "home_collap")
```

## Compose + ViewModel (MVI)

GMA cần **Activity + ViewGroup**. ViewModel không giữ chúng. Tách:

| Lớp | Việc |
|---|---|
| ViewModel | RC flags, preload (`applicationContext`), emit **effect** (show inter / reward) và **state** (bật/tắt slot banner/native) |
| Compose | `collect` effect → `AdsSdk.*` với `LocalActivity`; `if (state.showHomeBottom) AdsBottom(...)` |

```kotlin
// ViewModel
fun onBackHome() {
    if (!AdsSdk.remoteConfig.getBoolean("is_show_inter_back_home", false)) {
        _effects.trySend(HomeEffect.NavigateBack)
        return
    }
    _effects.trySend(HomeEffect.ShowInter(interId, ignoreInterval = true))
}

// Compose
LaunchedEffect(vm) {
    vm.effects.collect { effect ->
        when (effect) {
            is HomeEffect.ShowInter -> AdsSdk.interstitial.loadAndShow(
                activity, effect.adUnitId, effect.ignoreInterval,
                callback = object : AdCallback {
                    override fun onNextAction() { vm.onAdFinished() }
                },
            )
            is HomeEffect.NavigateBack -> navController.popBackStack()
        }
    }
}
if (state.showHomeBottom) AdsBottom(state.homeBottom, placement = "home_collap")
if (state.showNative) AdsNative(state.nativeUnitId, placement = "home")
```

`onNextAction()` đưa về ViewModel để navigation. Preload native lúc splash/VM `init` với `applicationContext`; Home chỉ `AdsNative(..., placement = "home")` bind ad đã cache.

Mẫu đầy đủ: `demo-compose` (`HomeViewModel` + `HomeEffect`).

## Interstitial interval

Content interstitials wait `ads_interval` seconds after the previous **content** dismiss. `ignoreInterval = true` skips the wait for that show (back-home). Open-as-inter and resume-as-inter never use the gate.

`onNextAction()` runs on show, fail, skip, and timeout so navigation cannot stall.

## Remote Config

Firebase **không fetch từng key**. SDK (hoặc app) `fetchAndActivate()` cả template, rồi đọc theo tên key.

| Policy | Behavior |
|---|---|
| `None` | Init không fetch. Vẫn có thể `AdsSdk.remoteConfig.fetch { }` rồi `getBoolean(key)` |
| `ReadOnly` | Không fetch; đọc key đã activate (app fetch trước) |
| `FetchAndRead` | `AdsSdk.init` tự fetch rồi `onReady` |

```kotlin
AdsSdk.init(
    this,
    AdsConfig(
        debug = true,
        remote = RemoteConfigPolicy.FetchAndRead,
        interstitialIntervalRemoteKey = "ads_interval", // hoặc interval_show_interstitial
        resumeRemoteKey = "show_resum_ads",
        openAds = OpenAdsConfig(
            enabledRemoteKey = "show_open_ads",
            firstOpenRemoteKey = "show_open_ads_first_open",
            typeIsInterRemoteKey = "show_opens_ads_type",
        ),
    ),
) {
    val rc = AdsSdk.remoteConfig
    val showBackHome = rc.getBoolean("is_show_inter_back_home", false)
    val intervalSec = rc.getLong("ads_interval", 15)
    val nativeOn = rc.getBoolean("is_show_native_onboarding", false)
    if (showBackHome) {
        AdsSdk.interstitial.loadAndShow(activity, interId, ignoreInterval = true, callback)
    }
}
```

App **phải** có `google-services.json` + plugin `com.google.gms.google-services`. Key khai trên Firebase Console (Boolean / Number / String). Flag từng màn (`is_show_*`) app tự đọc; SDK chỉ tự đọc interval / open ads / resume / collap reload.

Không fetch hai lần (SDK `FetchAndRead` + Splash `fetchAndActivate`) trừ khi chỉ một bên là owner.

## Publish (AAR / Maven)

Hai artifact:

| Module | Maven |
|---|---|
| `:ads-sdk` | `com.pirago.ads-helper:sdk:1.0.0-SNAPSHOT` |
| `:ads-sdk-compose` | `com.pirago.ads-helper:sdk-compose:1.0.0-SNAPSHOT` |

Đổi `adsSdk.group` / `adsSdk.version` trong `gradle.properties`. **Nên publish Maven (kèm POM)** chứ đừng chỉ copy file `.aar` — POM kéo theo GMA, UMP, Firebase Config. File AAR một mình sẽ thiếu dependency.

### 1. AAR file (gửi tay / commit `libs/`)

```bash
./gradlew exportAdsSdkAar
```

File nằm ở `build/dist/sdk-1.0.0-SNAPSHOT.aar` và `sdk-compose-1.0.0-SNAPSHOT.aar`. App nhận AAR phải tự thêm:

```kotlin
implementation("com.google.android.gms:play-services-ads:23.6.0")
implementation("com.google.android.ump:user-messaging-platform:4.0.0")
implementation("com.google.firebase:firebase-config:23.0.0")
```

`sdk-compose` còn cần Compose BOM + `activity-compose`.

### 2. Maven local (máy bạn / CI cùng máy)

```bash
./gradlew publishToMavenLocal
# hoặc
./gradlew publishAdsSdk
```

App khác:

```kotlin
repositories { mavenLocal() }
dependencies {
    implementation("com.yourorg.ads:sdk:1.0.0-SNAPSHOT")
    implementation("com.yourorg.ads:sdk-compose:1.0.0-SNAPSHOT") // optional
}
```

`publishAdsSdk` cũng ghi repo thư mục `build/maven-repo/` (POM + AAR + sources). Zip folder đó rồi trỏ:

```kotlin
repositories { maven { url = uri("/path/to/maven-repo") } }
```

### 3. Không user/password, không phí — GitHub Pages

Repo **public**. Consumer không khai credentials. Publisher không khai user/password trong Gradle — CI dùng `GITHUB_TOKEN` sẵn có.

Một lần trong GitHub: **Settings → Pages → Source = GitHub Actions**.

```bash
git tag v1.0.0
git push origin v1.0.0
```

Workflow `.github/workflows/publish-maven-pages.yml` build AAR và deploy `build/maven-repo` lên Pages. App đích:

```kotlin
repositories {
    google()
    mavenCentral()
    maven { url = uri("https://USER.github.io/REPO/") }
}
dependencies {
    implementation("com.yourorg.ads:sdk:1.0.0")
    implementation("com.yourorg.ads:sdk-compose:1.0.0") // optional
}
```

Nếu không muốn public source: tạo repo **chỉ chứa** `maven-repo` (public), source để private. Local:

```bash
./gradlew publishAdsSdk
# copy build/maven-repo → repo public, push
```

### 4. Maven remote có auth (GitHub Packages / Nexus)

Chỉ khi cần registry riêng. Trong `gradle.properties` hoặc env:

```
adsSdk.mavenUrl=https://maven.pkg.github.com/OWNER/REPO
adsSdk.mavenUser=YOUR_GITHUB_USERNAME
adsSdk.mavenPassword=YOUR_GITHUB_TOKEN
```

Hoặc `ADS_SDK_MAVEN_URL` / `ADS_SDK_MAVEN_USER` / `ADS_SDK_MAVEN_PASSWORD`. Rồi:

```bash
./gradlew publishAdsSdk
```

App consumer thêm cùng `repositories { maven { url = ...; credentials { ... } } }`.

## Run samples

```bash
./gradlew :demo-xml:installDebug
./gradlew :demo-compose:installDebug
./gradlew :ads-sdk:test
```

Samples use Google test ad units.

## JBase demo (`:app`)

Unchanged. Still wraps obfuscated AARs via reflection and a `UnityPlayer` shim. Do not use that path for the shipped SDK.
