# Ads SDK (AdMob + optional MAX, XML + Compose)

Android library in this repo: **`:ads-sdk`**. Optional Compose: **`:ads-sdk-compose`**. Optional AppLovin MAX: **`:ads-sdk-max`**. Samples: **`:demo-xml`**, **`:demo-compose`** (GMA only). The original JBase AAR demo remains in **`:app`**.

Current version: **1.0.4**. Artifact `sdk` is GMA-owned (no AppLovin on the classpath). Add `sdk-max` only if the app supports Firebase `mediation_type = 0`. No UnityPlayer, no JBase AAR wrap.

### 1.0.4

- Thêm `AdsSdk.openAdInspector`. AdMob mở Ad Inspector; MAX mở Mediation Debugger. Chỉ khi `AdsConfig.isDebuggableAds` là true. Product để false thì no-op.

**App đích (Koin):** copy [SDK_INTEGRATION.md](SDK_INTEGRATION.md) vào chat AI của repo app — Gradle Pages, dual-engine, Haircut RC keys, XML/Compose, native custom.

## Modules

| Module | Artifact | Role |
|---|---|---|
| `:ads-sdk` | `com.pirago.ads-helper:sdk` | Public API, ViewGroup host, GMA |
| `:ads-sdk-compose` | `com.pirago.ads-helper:sdk-compose` | `AndroidView` helpers (`AdsBanner`, `AdsNative`, `AdsBottom`, `AdsMrec`) |
| `:ads-sdk-max` | `com.pirago.ads-helper:sdk-max` | Optional MAX bridge (Inter, AOA, Banner, MREC). App adds this for `mediation_type = 0` |
| `:demo-xml` | — | ViewBinding sample (GMA; no `sdk-max`) |
| `:demo-compose` | — | Compose sample (GMA; no `sdk-max`) |
| `:app` | — | Legacy JBase reflection demo |

## Init

App `AndroidManifest.xml` (not the library):

```xml
<meta-data
    android:name="com.google.android.gms.ads.APPLICATION_ID"
    android:value="ca-app-pub-xxxxxxxx~yyyyyyyyyy" />
```

```kotlin
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
                interstitialIntervalSec = 15,
                interstitialIntervalRemoteKey = "interval_show_interstitial", // Haircut: "ads_interval"
                enableResumeAds = true,
                resumeFormat = ResumeFormat.AppOpen, // or Interstitial
                // Haircut: resumeTypeRemoteKey = "resume_type" (string). Do not put that on resumeRemoteKey.
                admob = AdmobAdUnits(/* banner, native, interstitial, rewarded, appOpen */),
                // If the app supports MAX, also: max = MaxAdUnits(...), mediationRemoteKey = "mediation_type"
                remote = RemoteConfigPolicy.None, // ReadOnly / FetchAndRead
                revenueLogger = LogcatRevenueLogger(),
                funnelLogger = LogcatFunnelLogger(),
            ),
        )
    }
}
```

SDK là `object`, **không** cần bind Hilt/`@HiltAndroidApp`. App đích dùng **Koin**: gọi `AdsSdk.init` trong `Application.onCreate` (cùng chỗ `startKoin`). Không extend `AdsApplication` nếu `Application` đã bị Koin/`androidContext` chiếm — `AdsSdk.init` là API chính.

Có thể wrap config trong Koin module nếu muốn inject `AdsConfig`, nhưng GMA show API vẫn cần `Activity` từ UI layer, không inject Activity vào SDK.

Placement kill-switches (`is_show_inter_*`, `is_show_mrec_*`, `is_load_native_*`) stay in the **app**. SDK reads global knobs (`mediation_type`, interval, open/resume type, collap reload). After RC: `AdsSdk.isMax` and `AdsSdk.units.*` pick GMA or MAX IDs. Native / rewarded / bottom collap = AdMob only; MREC = MAX only.

## Splash / consent / open ads

```kotlin
AdsSdk.appOpen.disableResumeWith(SplashActivity::class.java)
AdsSdk.consent.obtainAndShow(this) {
    if (!AdsSdk.isMax) {
        AdsSdk.native.preload(applicationContext, "home_collap", AdsSdk.units.native)
    }
    AdsSdk.openAds.showIfEligible(this, object : AdCallback {
        override fun onNextAction() { /* navigate — always called */ }
    })
}
```

Open ads (Led Banner boolean): first launch needs `show_open_ads` AND `show_open_ads_first_open`. `show_opens_ads_type` true = Inter, false = App Open. Haircut: `formatRemoteKey = "aoa_type"` (`inter` / `aoa` / empty = off). Those shows do **not** consume `ads_interval`. Resume Haircut uses `resumeTypeRemoteKey = "resume_type"` and `resume_ads_interval`.

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

Custom native XML must contain a `NativeAdView` (as root, or nested with id `nativeAdView` / `ads_sdk_native_ad_view` / `native_ad_view`) and map GMA assets with these IDs:

| Asset | SDK id | DIY / Google / Pirago (Themie) aliases |
|---|---|---|
| Headline (required) | `ads_sdk_headline` | `adHeadline`, `ad_headline`, `native_ad_headline` |
| Body | `ads_sdk_body` | `adBody`, `ad_body`, `native_ad_body` |
| CTA | `ads_sdk_cta` | `adCallToAction`, `ad_call_to_action`, `native_ad_call_to_action` (+ nested `native_ad_call_to_action_text` if CTA is `ViewGroup` / `FrameLayout`) |
| Icon | `ads_sdk_icon` | `adIcon`, `ad_icon`, `native_ad_icon`, `adAppIcon` |
| Media | `ads_sdk_media` | `adMedia`, `ad_media`, `native_ad_media` |
| Advertiser | `ads_sdk_advertiser` | `adAdvertiser`, `ad_advertiser` |
| Stars | `ads_sdk_stars` | `adStarRating`, `ad_stars` |
| AdChoices | `ads_sdk_ad_choices` | `ad_choices_container`, `ad_choices_view` |

Optional host chrome after bind: `native_ad_content_root` → `VISIBLE` (kể cả khi XML để `invisible`), `native_loading_root` → `GONE`.

### Pirago / Themie layouts (đã verify)

App có thể truyền thẳng XML sẵn có — **không rename ID**:

| App layout | Gọi SDK |
|---|---|
| `layout_native_ad_medium.xml` | `loadAndBind(..., R.layout.layout_native_ad_medium)` |
| `layout_native_ad_small.xml` | `loadAndBind(..., R.layout.layout_native_ad_small)` |
| `layout_native_medium_language.xml` | `loadAndBind(..., R.layout.layout_native_medium_language)` |
| `layout_native_ad_fullscreen.xml` | `loadAndBind(..., R.layout.layout_native_ad_fullscreen)` — asset bind OK (`adAppIcon`, DIY headline). **`btnCloseAd` countdown vẫn app** |
| `layout_native_ad_collapsible.xml` | Dùng như **expanded content** (`expandedLayoutRes`), **không** thay chrome SDK |

Home bottom / collap — chrome SDK (`ads_native_collapsible`) + XML app cho expanded/collapsed:

```kotlin
BottomAdConfig(
    showNativeSmall = true,
    nativeAdUnitId = id,
    banner = BannerConfig(bannerId, BannerType.Adaptive),
    collapsible = true,
    reloadSec = 15,
    expandedLayoutRes = R.layout.layout_native_ad_medium,       // hoặc layout_native_ad_collapsible
    collapsedLayoutRes = R.layout.layout_native_ad_small,
)
```

**Không** truyền `layout_native_ad_collapsible` làm toàn bộ chrome — layout đó không có `ads_sdk_collap_expanded` / nút collapse.

App-owned (SDK không port): badge `AD`, blur `adMediaBackground`, `btnCloseAd` countdown, Coil. Banner / interstitial / AOA / rewarded = UI Google — không custom XML.

## Compose

```kotlin
import com.ads.sdk.compose.AdsBanner
import com.ads.sdk.compose.AdsBottom
import com.ads.sdk.compose.AdsMrec
import com.ads.sdk.compose.AdsNative

if (AdsSdk.isMax) {
    AdsMrec(adUnitId = AdsSdk.units.mrec)
} else {
    AdsNative(adUnitId = AdsSdk.units.native, template = NativeTemplate.Small)
    AdsBottom(homeBottomConfig, placement = "home_collap")
}
AdsBanner(BannerConfig(AdsSdk.units.banner, BannerType.CollapsibleBottom))
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

App **phải** có `google-services.json` + plugin `com.google.gms.google-services`. Key khai trên Firebase Console (Boolean / Number / String). Flag từng màn (`is_show_*`) app tự đọc; SDK đọc `mediation_type`, interval / open ads / resume / collap reload. Haircut init mẫu: [SDK_INTEGRATION.md](SDK_INTEGRATION.md) §5.

Không fetch hai lần (SDK `FetchAndRead` + Splash `fetchAndActivate`) trừ khi chỉ một bên là owner.

## Publish (AAR / Maven)

Ba artifact (`adsSdk.version` hiện **1.0.4**):

| Module | Maven |
|---|---|
| `:ads-sdk` | `com.pirago.ads-helper:sdk:1.0.4` |
| `:ads-sdk-compose` | `com.pirago.ads-helper:sdk-compose:1.0.4` |
| `:ads-sdk-max` | `com.pirago.ads-helper:sdk-max:1.0.4` |

Đổi `adsSdk.group` / `adsSdk.version` trong `gradle.properties`. **Nên publish Maven (kèm POM)** chứ đừng chỉ copy file `.aar` — POM kéo theo GMA, UMP, Firebase Config (`sdk-max` kéo AppLovin). File AAR một mình sẽ thiếu dependency.

### 1. AAR file (gửi tay / commit `libs/`)

```bash
./gradlew exportAdsSdkAar
```

File nằm ở `build/dist/sdk-1.0.4.aar`, `sdk-compose-1.0.4.aar`, `sdk-max-1.0.4.aar`. App nhận AAR `sdk` phải tự thêm:

```kotlin
implementation("com.google.android.gms:play-services-ads:23.6.0")
implementation("com.google.android.ump:user-messaging-platform:4.0.0")
implementation("com.google.firebase:firebase-config:23.0.0")
```

`sdk-compose` còn cần Compose BOM + `activity-compose`. `sdk-max` còn cần `com.applovin:applovin-sdk` (POM đã khai nếu dùng Maven).

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
    implementation("com.pirago.ads-helper:sdk:1.0.4")
    implementation("com.pirago.ads-helper:sdk-compose:1.0.4") // optional
    implementation("com.pirago.ads-helper:sdk-max:1.0.4")     // optional MAX
}
```

`publishAdsSdk` cũng ghi repo thư mục `build/maven-repo/` (POM + AAR + sources). Zip folder đó rồi trỏ:

```kotlin
repositories { maven { url = uri("/path/to/maven-repo") } }
```

### 3. GitHub Pages (đang dùng)

Maven public, không credentials:

- Source + Pages: [github.com/ka1toz/pi-ads-helper](https://github.com/ka1toz/pi-ads-helper)
- URL Maven: `https://ka1toz.github.io/pi-ads-helper/`
- Nhánh **source**: làm việc bình thường (`feature/wrap-into-library`, `main`, …)
- Nhánh **`gh-pages`**: chỉ chứa nội dung `build/maven-repo` (POM + AAR). Không trộn source.

App đích:

```kotlin
repositories {
    google()
    mavenCentral()
    maven { url = uri("https://ka1toz.github.io/pi-ads-helper/") }
}
dependencies {
    implementation("com.pirago.ads-helper:sdk:1.0.4")
    implementation("com.pirago.ads-helper:sdk-compose:1.0.4") // optional
    implementation("com.pirago.ads-helper:sdk-max:1.0.4")     // optional MAX
}
```

Kiểm tra file đã lên Pages:

`https://ka1toz.github.io/pi-ads-helper/com/pirago/ads-helper/sdk/maven-metadata.xml`

Chi tiết bước build/release/update: mục **Build, release, update SDK** bên dưới. Workflow `.github/workflows/publish-maven-pages.yml` là phương án CI (Pages = GitHub Actions); hiện tại release thủ công qua nhánh `gh-pages`.

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

## Build, release, update SDK

Mục này để **AI hoặc người** làm theo khi có bản SDK mới. Không đoán version. Không `--force` push. Không xóa version cũ trên `gh-pages`.

### Tọa độ (không đổi trừ khi được yêu cầu)

| | |
|---|---|
| Group | `com.pirago.ads-helper` (`adsSdk.group` trong `gradle.properties`) |
| Artifacts | `sdk`, `sdk-compose`, `sdk-max` |
| GitHub | `git@github.com:ka1toz/pi-ads-helper.git` |
| Maven URL | `https://ka1toz.github.io/pi-ads-helper/` |
| Nhánh source | nhánh đang làm việc (đừng checkout `gh-pages` trong worktree chính) |
| Nhánh Maven | `gh-pages` — chỉ file từ `build/maven-repo/` |

App đích: `implementation("com.pirago.ads-helper:sdk:X.Y.Z")` + repo `https://ka1toz.github.io/pi-ads-helper/`.

### Build / test (mọi PR)

Từ root repo:

```bash
./gradlew :ads-sdk:test :ads-sdk:assembleRelease :ads-sdk-compose:assembleRelease :ads-sdk-max:assembleRelease
```

Demo (không bắt buộc khi release):

```bash
./gradlew :demo-xml:assembleDebug :demo-compose:assembleDebug
```

### Release version mới (ví dụ `1.0.4`)

Thay `VERSION` bằng số semver **chưa từng publish**. Patch = fix; minor = API mới tương thích; major = breaking. **Không** ghi đè folder version cũ trên `gh-pages` (giữ `1.0.2/`).

1. Sửa `adsSdk.version` trong `gradle.properties` thành `VERSION` (bỏ `-SNAPSHOT` khi phát hành).
2. Chạy test + publish:

```bash
VERSION=1.0.4   # đổi số này
./gradlew :ads-sdk:test
./gradlew publishAdsSdk -PadsSdk.version="$VERSION"
```

3. Merge vào nhánh `gh-pages` (**giữ** thư mục version cũ). Dùng clone tạm, không `git checkout gh-pages` trong source tree:

```bash
VERSION=1.0.4
MAVEN_DIR=/tmp/pi-ads-helper-maven
rm -rf "$MAVEN_DIR"
git clone --branch gh-pages --single-branch git@github.com:ka1toz/pi-ads-helper.git "$MAVEN_DIR"
rsync -a --exclude '.git' build/maven-repo/ "$MAVEN_DIR/"
cd "$MAVEN_DIR"
git add .
git status   # phải thấy com/pirago/ads-helper/sdk/$VERSION/ và sdk-max/$VERSION/ ; không được xóa sdk/1.0.2/
git commit -m "Publish ads-helper $VERSION"
git push origin gh-pages
```

Nếu clone `gh-pages` thất bại (chưa có nhánh): lần đầu tạo orphan từ `build/maven-repo` như mục GitHub Pages ở trên, rồi `git push -u origin gh-pages`.

4. Commit source (version trong `gradle.properties`) + tag, đẩy GitHub:

```bash
VERSION=1.0.4
git add gradle.properties README.md
git commit -m "Release ads-helper $VERSION"
git tag "v$VERSION"
git push origin HEAD
git push origin "v$VERSION"
# nếu remote GitHub tên khác origin:
# git push origin-github HEAD && git push origin-github "v$VERSION"
```

5. Đợi ~1 phút, mở:

`https://ka1toz.github.io/pi-ads-helper/com/pirago/ads-helper/sdk/maven-metadata.xml`

Phải thấy `$VERSION`. App đích đổi `implementation` sang version mới rồi sync Gradle.

### Lần đầu tạo `gh-pages` (chỉ khi nhánh chưa tồn tại)

```bash
./gradlew publishAdsSdk -PadsSdk.version=1.0.0
cd build/maven-repo
git init
git checkout -b gh-pages
git add .
git commit -m "Publish ads-helper 1.0.0"
git remote add origin git@github.com:ka1toz/pi-ads-helper.git
git push -u origin gh-pages
```

GitHub → Settings → Pages → **Deploy from a branch** → `gh-pages` / `(root)`.

### Việc AI không được làm

- `git push --force` lên `gh-pages` hoặc `main`
- Xóa folder version cũ trong `com/pirago/ads-helper/sdk/`
- Đổi `adsSdk.group` hoặc Maven URL nếu không được hỏi
- Publish `-SNAPSHOT` lên Pages như bản production
- Commit `local.properties`, keystore, `google-services.json` production

## Run samples

```bash
./gradlew :demo-xml:installDebug
./gradlew :demo-compose:installDebug
./gradlew :ads-sdk:test
```

Samples use Google test ad units.

## JBase demo (`:app`)

Unchanged. Still wraps obfuscated AARs via reflection and a `UnityPlayer` shim. Do not use that path for the shipped SDK.
