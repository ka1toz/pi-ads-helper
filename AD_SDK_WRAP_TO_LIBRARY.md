# Plan: Android Ads SDK (Compose + XML)

Tài liệu này là **source of truth** để đóng gói thư viện ads dùng cho app Android thuần.

- Engine tham khảo (không ship binary Unity): `sub-ads-demo` / AAR JBase — chỉ học Init, interval, AOA, UMP, paid-event.
- Consumer tham khảo: Led Banner (`/Users/vu/projects/android/led-banner/ledbanner`) gọi `com.nlbn.ads` / VTN 2.0.0.
- Brief đối tác điển hình: `Pirago_Dog Translator_Monet.xlsx` (sheet AdRemoteEvent + Feedback) — pattern placement + RC + native collap + funnel AF.
- **Không copy** package `com.nlbn.ads`, allowlist `package_apps.json`, hay `isLoadFullAds` organic/paid.

Cập nhật so với bản plan trước: **bỏ UnityPlayer, v1 chỉ AdMob, banner/native gắn `ViewGroup` để XML và Compose dùng chung.**

---

## Review bản plan cũ — hợp lý / chưa hợp lý

| Điểm plan cũ | Đánh giá |
|---|---|
| Public API giống cách Led Banner nghĩ (Application, UMP, splash timeout, `onNextAction`, native bind, banner vào container, AOA exclude Activity) | **Giữ** — đúng nhu cầu app |
| Placement kill-switch (`is_load_native_home`…) nằm **ở app**, SDK không nuốt 20 flag | **Giữ** |
| Không copy VTN allowlist / IAP / RateDialog / Firestore | **Giữ** |
| Dual AdMob + MAX + bọc 6 AAR JBase làm `:ads-engine` v1 | **Bỏ** — AAR JBase đọc `UnityPlayer.currentActivity`; banner dính `android.R.id.content`; native AdMob **không có** trong `AdmobHelper` |
| “Keep Unity shim nội bộ, app không thấy” | **Bỏ theo yêu cầu** — SDK Android-only, không phụ thuộc `com.unity3d.player` |
| Demo reflection / Init AdMob sai overload | Đúng nợ, nhưng không phải nền tảng library |
| `onNextAction()` luôn chạy kể cả fail | **Giữ** — Led Banner navigation phụ thuộc cái này |

**Kết luận:** hình API consumer (VTN-style) hợp lý. Hình **engine = AAR JBase** không hợp lý nếu muốn thư viện Android + Compose/XML. v1 viết **GMA owned** (Google Mobile Ads) trong `:ads-sdk`. AAR JBase chỉ là tài liệu hành vi (interval, resume AOA, RC keys game). MAX / mediation = phase sau, module optional.

---

## 1. Mục tiêu

1. Artifact Gradle: `com.yourorg.ads:sdk:<version>` — app `implementation`, gọi API typed.
2. Host **XML View** và **Jetpack Compose** cùng một API. SDK **không** phụ thuộc Compose. Compose chỉ `AndroidView` bọc `ViewGroup`.
3. AdMob thuần (v1). Không Unity, không reflection, không `UnityPlayer`.
4. Đủ port Led Banner: splash inter + timeout, App Open resume + exclude Activity, banner adaptive/collapsible **vào container**, native custom layout, inter interval, consent UMP.
5. Paid-event pluggable (Firebase + AppsFlyer hoặc Adjust — Led Banner đang Adjust; demo JBase đang AppsFlyer).

### Không làm (v1)

- MAX / waterfall mediation **SDK khác** trong artifact mặc định. **AdMob Mediation** (adapter GMA) app tự thêm — OK v1.
- Redistribute 6 AAR JBase obfuscated.
- Clone `com.nlbn.ads` drop-in (có thể làm `:ads-compat-vtn` sau).
- IAP tắt ads, rate dialog, `isLoadFullAds` organic.
- Hard-code “show ads everywhere” — policy do app + Remote Config app.

---

## 2. Catalog tính năng SDK phải có

Chia 3 tầng: **SDK bắt buộc**, **SDK nên có**, **app (không nhét vào SDK)**.

### 2.1 Bắt buộc (v1) — Led Banner đang gọi thật

| Nhóm | Tính năng | API gợi ý | Ghi chú Compose / XML |
|---|---|---|---|
| Init | Init SDK trong `Application` | `AdsSdk.init(application, AdsConfig)` | Không cần UI |
| Init | AdMob App ID | Manifest `<meta-data APPLICATION_ID>` — app tự khai | Library không hard-code |
| Init | Test device IDs + debug | `AdsConfig.testDeviceIds`, `debug` | |
| Init | Lifecycle Activity | `ActivityLifecycleCallbacks` nội bộ | Cần Activity cho fullscreen; Compose `ComponentActivity` OK |
| Init | Remote Config (SDK knobs) | `AdsConfig.remote` — xem §2.4 | Không UI; Compose/XML như nhau |
| Consent | UMP gather + form | `Consent.obtainAndShow(activity) { }` | Fullscreen UMP; XML/Compose như nhau |
| Consent | `canRequestAds()` | Trước mọi load | App tự gate thêm flag RC |
| Consent | `reset()` | Debug / khi chưa consent | |
| Splash inter | Load + timeout | `loadSplashInter(activity, id, timeoutMs, cb)` | Fullscreen |
| Splash inter | Show lại khi fail / onResume | `onCheckShowSplashWhenFail(activity, cb, delayMs)` | Led Banner `SplashActivity.onResume` |
| Splash inter | Dismiss loading dialog | `dismissLoadingDialog()` | Optional overlay |
| Inter | Preload | `loadInter(activity, id, cb)` | |
| Inter | Show nếu ready | `showInter(activity, cb)` | |
| Inter | Load-and-show | `loadAndShowInter(activity, id, ignoreInterval, cb)` | `force=true` = bỏ cooldown (inter back Led Banner) |
| Inter | Interval / cooldown | `AdsConfig.interstitialIntervalSec` + RC key | Led Banner: `interval_show_interstitial`; Pirago: `ads_interval` default 15s — **tính từ lúc dismiss inter → show inter kế** |
| Inter | Interval **không** áp dụng | Open-as-Inter + Resume-as-Inter | Brief: `ads_interval` **không** chặn khi `show_opens_ads_type=TRUE` (inter lúc mở app) hoặc `show_resum_ads=TRUE` |
| Inter | Callback không kẹt nav | `onNextAction()` **luôn** gọi (show / fail / skip) | Quan trọng hơn `onAdClosed` |
| Rewarded | Load / show | `rewarded.load` / `show` + `onUserEarnedReward` | Đối tác hay thêm RW sau; funnel `af_rewarded_*` |
| Banner | Gắn vào `ViewGroup` | `loadBanner(activity, container, shimmer?, BannerConfig)` | XML: `FrameLayout`. Compose: `AndroidView { FrameLayout(it) }` rồi truyền container |
| Banner | Adaptive | `BannerType.Adaptive` | |
| Banner | Collapsible top/bottom | `BannerType.CollapsibleBottom / Top` | Extra GMA `collapsible`; **không** reflow Compose lúc expand |
| Banner | Refresh / CB fetch interval | `refreshSec`, `collapsibleFetchIntervalSec` | Led Banner lấy `cb_fetch_interval` |
| Banner | Hide / destroy | `hideBanner(container)` / `destroyBanner(container)` | RC tắt giữa session |
| Native | Load | `loadNative(context, id, cb)` | Preload 1 ad / placement |
| Native | Bind custom layout | `bindNative(nativeAd, NativeAdView)` | App inflate XML `NativeAdView` (headline/body/CTA/icon/media) |
| Native | Inflate + bind helper | `bindNative(activity, layoutRes, container, nativeAd)` | Giống `AdsConfig.pushNativeAdToView` |
| Native | Template Small / Medium / Fullscreen | `NativeTemplate` + layout SDK ship **hoặc** app `layoutRes` | Brief đối tác: Medium 2 thứ tự asset; Full màn onboarding; Small icon+title+desc+CTA |
| Native | Collapsible → Small | `loadNativeCollapsible(activity, container, NativeCollapConfig)` | **Không** phải GMA collapsible banner. Collapse button → Native Small; `time_reload_collap_ad` reload/re-expand |
| Native | Destroy / TTL cache | cache theo `placement`, `destroy()` lúc unbind | **Không** static `AdsConfig.nativeAdsHome` trong SDK |
| Native | Preload trước Home | `native.preload(placement)` lúc splash | Feedback đối tác: collap Home phải hiện ngay khi vào Home |
| App Open | Enable resume ads | `AdsConfig.enableResumeAds` + ad unit | |
| App Open | Resume format | `ResumeFormat.AppOpen \| Interstitial` | Brief Pirago: resume **dạng Inter**; Led Banner: AOA. SDK hỗ trợ cả hai |
| App Open | Exclude Activity | `disableResumeWith(SplashActivity::class.java)` | Led Banner: Splash, Main (khi collapsible?), Permission, Create, Share… |
| App Open | Enable lại Activity | `enableResumeWith(...)` | |
| App Open | Tắt/bật global | `disableResume()` / `enableResume()` | |
| Open / splash | Cold start AOA **hoặc** Inter | `OpenAdsConfig` + RC `show_opens_ads_type` | TRUE = Inter, FALSE = AOA. AND `show_open_ads` × `show_open_ads_first_open` |
| Open / splash | First open vs lần sau | `isFirstOpen` persist | First open chỉ show khi **cả hai** flag true; lần 2+ chỉ `show_open_ads` |
| Bottom ad | Native Small **hoặc** Banner | `loadBottomAd(container, BottomAdConfig)` | RC `is_show_native_small`: true = Native Small, false = Banner |
| Network | `isOnline()` | Trước load | |
| Revenue | Impression paid | Listener nội bộ → `RevenueLogger` | Firebase `ad_impression` (value, currency, ad_platform, ad_source, ad_format, ad_unit_name) + AppsFlyer `af_ad_revenue` / Adjust |
| Revenue | Funnel AppsFlyer | `af_inters_*` / `af_rewarded_*` | eligible → api_called → displayed — đối tác **bắt** trên mọi app monet |
| Revenue | Ad click log | Optional | |
| ProGuard | consumer-rules | keep GMA / UMP | App minify không gãy |

### 2.2 Nên có (v1 nếu rảnh, hoặc v1.1)

| Tính năng | Lý do |
|---|---|
| Native Fullscreen onboarding chrome | Layout full + chỗ **Next của app** ngoài `NativeAdView` (Next không được tính ad click) |
| MREC vào `ViewGroup` | Game; brief Dog Translator không dùng |
| Preload queue native (1 backup) | Led Banner reload khi `onAdImpression`; collap Home cần sẵn ad |
| Loading dialog khi `loadAndShowInter` | VTN có; default **off** |
| AdMob Mediation adapters | Header brief: “Sử dụng Admob Mediation”. v1 GMA + mediation **trên console AdMob** (adapter app thêm). MAX / waterfall SDK khác = phase sau |
| `AdsApplication` base class | Optional — app Hilt (`@HiltAndroidApp`) không inherit được 2 Application; **ưu tiên** `AdsSdk.init` từ `Application.onCreate` |
| Module `:ads-sdk-compose` | `BannerAd(modifier, config)`, `NativeAd(layoutRes)` = `AndroidView` convenience — **không** bắt buộc |

### 2.3 Việc của app — không đưa vào SDK

| Việc | Ví dụ Led Banner / Pirago brief |
|---|---|
| Ad unit strings (mặc định) | `ads_id.xml` — Led Banner **không** lấy ad unit từ RC |
| Flag từng màn / từng trigger | `is_load_native_home`… hoặc `is_show_inter_translate`, `is_show_native_onboarding`… |
| Fetch RC cho **app flags** | Splash `initRemoteConfig`; Firebase Console **folder** Inter / AOA / Resume / Native |
| Map placement → screen | Catalog Excel: Screen + Position + Time — **app** gọi SDK đúng chỗ |
| Gate nghiệp vụ trước ads | Record ≥ 3s mới inter translate; timer Fake Call = 0 mới inter |
| In-app review | RC `rating_popup` — Play Core, không phải ads |
| Event product | `count_translator_X`, `first_loading_complete` — Analytics app |
| Chọn layout native full vs small | App chọn `NativeTemplate` / `layoutRes`; SDK không hard-code `isLoadFullAds` organic |
| Force update / IAP | `force_update`, billing |
| AppsFlyer/Adjust token | `AdsConfig` truyền vào, SDK không đọc `strings.xml` app |
| Padding Compose khi banner/native collapsed | `Modifier.padding(bottom = height)` — SDK báo height callback |

### 2.4 Remote Config — VTN có nhét vào SDK không? Có nên nhét?

**Kết luận:** VTN **có** phụ thuộc `firebase-config` (BOM 33.1.0) và **fetch RC bên trong ads AAR**. Nên đưa RC vào SDK **hẹp**, chỉ cho knob SDK sở hữu — **không** nuốt 20 flag placement. **Không** bắt buộc `AdsApplication` tự `fetchAndActivate` nếu app đã fetch (Led Banner đang fetch **2 lần**).

#### VTN làm gì (AAR `ads:2.0.0`, decompile)

`AdsApplication.onCreate`:

1. `FirebaseRemoteConfig.setDefaultsAsync(R.xml.remote_config_defaults)` — XML trong AAR gần như **rỗng** (`<defaults><entry/></defaults>`).
2. `fetchAndActivate()`.
3. Đọc `getKeyRemoteIntervalShowInterstitial()` (Led Banner trả `"interval_show_interstitial"`) → `Admob.setIntervalShowInterstitial`. Fail → default **20**.
4. Nếu `enableRemoteAdsResume()`: đọc **ad unit AOA từ RC string** (`getKeyRemoteAdsResume()`).

Các API khác trong SDK:

| API | Hành vi |
|---|---|
| `RemoteAdmobImpl.getIdAdsWithKey(key)` | `FirebaseRemoteConfig.getString(key)` — **ad unit ID từ RC**, không phải XML |
| `BannerPlugin.Config.configKey` | JSON RC: `ad_unit_id`, `type`, `refresh_rate_sec`, `cb_fetch_interval_sec` |
| `CommonFirebase.getBoolean/Long/String` | Helper đọc singleton RC |

Led Banner **vẫn fetch RC lần nữa** ở `SplashActivity.initRemoteConfig()` cho `is_load_*` / `force_update`, và tự đọc `cb_fetch_interval` rồi truyền `BannerPlugin.Config`. Ad unit native/banner/inter lấy từ `ads_id.xml`, **không** đi `RemoteAdmobImpl`.

→ RC **tách đôi**: SDK = interval + optional resume ID + optional banner JSON; **app** = kill-switch từng màn.

#### Có hợp lý nếu SDK mình cũng nhét RC?

**Có, nếu giới hạn.** Interval / collapsible cooldown / bật resume là knob toàn cục — PM đổi Firebase không cần ship app. Flag `is_load_native_home` là **product map**, mỗi app khác nhau — để app.

**Không copy nguyên VTN** vì:

1. **Hai lần `fetchAndActivate`** (SDK Application + Splash) — race, `minimumFetchInterval`, splash apply flag trước khi SDK fetch xong.
2. `RemoteAdmob` (mọi ad unit là RC key) không phải đường Led Banner đang đi; XML defaults + RC override là đủ.
3. `google-services.json` / FirebaseApp vẫn **thuộc app**. SDK chỉ `compileOnly`/`api` `firebase-config`.
4. App Hilt có thể đã init Firebase/RC trước `AdsSdk.init`.

#### Policy SDK mình

```
AdsConfig.remote: RemoteConfigPolicy
  None          — không đụng Firebase; interval / resume ID do AdsConfig số/string
  ReadOnly      — không fetch; đọc key từ FirebaseRemoteConfig đã activate (app fetch)
  FetchAndRead  — SDK setDefaults + fetchAndActivate rồi đọc (giống VTN AdsApplication)
```

Key SDK được phép đọc (app truyền **tên key**, không hard-code). Hai họ tên hay gặp:

| Led Banner | Pirago / Dog Translator | Dùng cho |
|---|---|---|
| `interval_show_interstitial` | `ads_interval` (number, default 15) | Cooldown inter (có ngoại lệ open/resume) |
| `cb_fetch_interval` | `time_reload_collap_ad` (number, default 15) | Reload collapsible — **banner** (Led) vs **native collap** (Pirago) |
| optional resume ad unit | `show_resum_ads` (boolean) | Bật resume; format Inter hoặc AOA do `AdsConfig` |
| — | `show_open_ads`, `show_open_ads_first_open`, `show_opens_ads_type` | Cold start on/off, first-open AND, Inter vs AOA |
| — | `is_show_native_small` | Swap Native Small / Banner |
| optional JSON `configKey` banner | — | Type + refresh — v1.1 |

**Không** đọc `is_show_inter_*` / `is_show_native_*` / `is_load_*` (placement) trong SDK. **Không** đọc `rating_popup`.

`AdsSdk.init` phải **await** fetch (hoặc nhận `Task`) trước splash load — Led Banner splash timeout 30s vừa UMP vừa RC; SDK document: consent + RC ready rồi mới `loadSplashInter` / open ads.

Compose/XML: RC không ảnh hưởng UI.

### 2.5 Brief monet đối tác (Pirago_Dog Translator_Monet.xlsx) — gap vs plan cũ

Nguồn: sheet **AdRemoteEvent** + **Feedback** của `Pirago_Dog Translator_Monet.xlsx` (app Dog Translator). Đây là **kịch bản giao việc điển hình**, không phải spec Led Banner. Plan cũ chỉ cover VTN/Led Banner → thiếu vài capability SDK hay bị đối tác bắt khi nghiệm thu.

#### Brief yêu cầu gì (rút pattern)

| Layer | Pattern trong Excel | SDK hay App |
|---|---|---|
| Header | “Sử dụng **Admob Mediation**” | Console AdMob + adapter; không bắt MAX v1 |
| Catalog | Type × Screen × Position × Time + RC kill-switch, default **FALSE** | **App** map placement; SDK không hard-code tên màn |
| AOA / Open | Cold start + resume; RC first-open AND; **đổi format Inter/AOA** | **SDK** |
| Inter | Nhiều trigger (back home, after onboarding, switch feature, sau action…) + `ads_interval` 15s | App gọi `loadAndShow`; SDK interval + exempt |
| Native | Medium (2 thứ tự), Fullscreen giữa onboarding (Next **của app**), Small, **Native Collapsible** | **SDK** template + collap widget |
| Banner | Collapsible bottom trên “màn đầu feature” | SDK (đã có) |
| Swap | `is_show_native_small`: Native Small **hoặc** Banner cùng slot | **SDK** helper `loadBottomAd` |
| Analytics | Firebase `ad_impression` + AppsFlyer eligible/called/displayed + `af_ad_revenue` | **SDK** |
| Product | `count_*`, `first_loading_complete`, `rating_popup`, ngôn ngữ, UI | **App** |
| Feedback lặp | Thiếu placement, collap Home chậm, thiếu resume/RW dù app gốc không có, RC chưa nhóm folder | SDK preload + RW/resume API; app bổ sung flag |

#### Gap đã có trong plan (Led Banner) — giữ

Splash timeout, inter `onNextAction`, banner collapsible vào `ViewGroup`, native bind custom, AOA exclude Activity, interval, UMP, RC hẹp, Compose/XML cùng API.

#### Gap plan cũ **thiếu** so với brief — đã bổ sung catalog

1. **Native Collapsible** (collapse → Native Small, `time_reload_collap_ad` = 0 không tự hiện lại / >0 reload+re-expand). Khác GMA collapsible **banner**.
2. **Native template** Small / Medium / Fullscreen — Full màn onboarding: chrome Next nằm **ngoài** `NativeAdView`.
3. **Open ads orchestration:** `show_open_ads` ∧ (`first_open` ? `show_open_ads_first_open` : true); `show_opens_ads_type` Inter vs AOA.
4. **Resume dạng Inter** (brief) song song resume AOA (Led Banner).
5. **Interval exempt** cho open-as-inter và resume-as-inter.
6. **Bottom slot swap** Native Small vs Banner.
7. **Preload** native collap trước khi Home visible (nghiệm thu: “hiện ngay lập tức”).
8. **Funnel AppsFlyer** `af_inters_ad_eligible` / `af_inters_api_called` / `af_inters_displayed` (+ rewarded tương tự), không chỉ paid-event.
9. **Firebase `ad_impression` thủ công** đúng param Google (platform, source, format, unit, value, currency).
10. **Rewarded API** trong v1 — đối tác hay thêm kịch bản RW sau.
11. **AdMob Mediation** = GMA + adapter; khác MAX.

#### Logic RC open ads (copy từ brief — SDK phải implement đúng AND)

Lần mở **đầu**: show chỉ khi `show_open_ads_first_open == true` **và** `show_open_ads == true`.  
Lần **2+**: chỉ `show_open_ads`.  
Format slot đó: `show_opens_ads_type == true` → Inter, `false` → AOA.

Resume (`show_resum_ads`): lock/unlock, về từ Home/app khác, về từ màn detail ads. Brief này: **Inter**. Không ăn `ads_interval`.

Native collap `time_reload_collap_ad`: `0` = sau collapse chỉ Small, không tự collap lại; `>0` = sau N giây hiện collap lại **và** đang collap thì cũng reload ad mới.

---

## 3. Compose và XML — hợp đồng bắt buộc

SDK là **View + Activity**. Đó là cách GMA hoạt động. Compose không thay AdView.

```
XML:     FrameLayout/fr_ads  ──►  AdsSdk.loadBanner(activity, frAds, shimmer, config)
Compose: AndroidView { FrameLayout(ctx) }  ──►  cùng loadBanner(activity, thatFrameLayout, …)
```

| Format | XML | Compose |
|---|---|---|
| Inter / Reward / AOA / Splash / UMP | `Activity` | `ComponentActivity` — **không** cần Composable ad |
| Banner / MREC / Native | `ViewGroup` trong layout | `AndroidView` tạo `FrameLayout` / inflate `NativeAdView` XML |
| Collapsible expand | Overlay AdView | Overlay; **đừng** nhét vào `LazyColumn` |

Cấm:

- `implementation` Compose trong `:ads-sdk` core.
- Banner gắn `android.R.id.content` (kiểu JBase) — **gãy** yêu cầu “nhúng vào chỗ app chỉ định”.
- Gán `id = android.R.id.content` cho view con (demo cũ).

Optional `:ads-sdk-compose`:

```kotlin
@Composable
fun AdsBanner(config: BannerConfig, modifier: Modifier = Modifier) {
    val activity = LocalActivity.current as Activity
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { FrameLayout(it) },
        update = { AdsSdk.banner.load(activity, it, shimmer = null, config) },
        onRelease = { AdsSdk.banner.destroy(it) }
    )
}
```

Sample repo: **một** Activity XML + **một** Activity Compose, cùng ad unit test.

---

## 4. Kiến trúc đã chốt

```
App XML  ──┐
           ├──►  :ads-sdk          (public API, GMA owned, ViewGroup host)
App Compose┘         │
                     ├── UMP
                     ├── Inter / Splash / Reward / AOA / Resume-as-Inter
                     ├── Banner + Native (templates, collap → small)
                     └── RevenueLogger + FunnelLogger
                            │
                     GMA + UMP + (optional) Firebase Analytics
```

MAX / JBase AAR **không** nằm trên đường đi v1.

Hilt: Led Banner `class App : AdsApplication()`. App Hilt không extend 2 class → `AdsSdk.init(this, config)` trong `onCreate()` là API chính. `AdsApplication` chỉ convenience cho app không Hilt.

```
ad-sdk/
  ads-sdk/                 # Android library, minSdk 24
  ads-sdk-compose/         # optional, api(ads-sdk) + compose
  demo-xml/                # sample ViewBinding
  demo-compose/            # sample setContent (sub-ads-demo rút gọn)
```

Publish: `com.yourorg.ads:sdk` và optional `com.yourorg.ads:sdk-compose`.

---

## 5. Public API v1 (sketch)

```kotlin
object AdsSdk {
    fun init(application: Application, config: AdsConfig)
    val consent: ConsentController
    val interstitial: InterstitialAds
    val splash: SplashAds
    val banner: BannerAds
    val native: NativeAds
    val appOpen: AppOpenAds
    val rewarded: RewardedAds
}

data class AdsConfig(
    val debug: Boolean,
    val testDeviceIds: List<String> = emptyList(),
    val appsFlyerDevKey: String? = null,       // hoặc adjustToken — một revenue backend
    val adjustToken: String? = null,
    val interstitialIntervalSec: Int = 15,
    val interstitialIntervalRemoteKey: String? = "interval_show_interstitial", // hoặc ads_interval
    val enableResumeAds: Boolean = true,
    val resumeAdUnitId: String? = null,
    val resumeFormat: ResumeFormat = ResumeFormat.AppOpen, // Interstitial nếu brief Pirago
    val openAds: OpenAdsConfig = OpenAdsConfig(),
    val remote: RemoteConfigPolicy = RemoteConfigPolicy.ReadOnly,
    val revenueLogger: RevenueLogger? = null,
    val funnelLogger: FunnelLogger? = null,    // AppsFlyer af_inters_* / af_rewarded_*
)

data class OpenAdsConfig(
    val enabledRemoteKey: String? = "show_open_ads",
    val firstOpenRemoteKey: String? = "show_open_ads_first_open",
    val typeIsInterRemoteKey: String? = "show_opens_ads_type", // true=Inter, false=AOA
    val firstOpenAdUnitId: String? = null,
)

enum class NativeTemplate { Small, Medium, MediumCtaFirst, Fullscreen }

data class NativeCollapConfig(
    val expandedUnitId: String,
    val collapsedUnitId: String,               // Native Small sau collapse
    val reloadSecRemoteKey: String? = "time_reload_collap_ad",
    val reloadSec: Int = 15,                   // 0 = không tự expand lại
)

interface AdCallback {
    fun onNextAction()                         // LUÔN gọi
    fun onAdLoaded() {}
    fun onAdFailedToLoad(error: AdError?) {}
    fun onAdShown() {}
    fun onAdImpression() {}
    fun onAdClicked() {}
    fun onAdDismissed() {}
}

data class BannerConfig(
    val adUnitId: String,
    val type: BannerType,                      // Adaptive | CollapsibleBottom | CollapsibleTop | Standard
    val refreshSec: Int = 0,
    val collapsibleFetchIntervalSec: Int = 0,
)
```

Manifest app (bắt buộc, không nhét vào AAR library như giá trị cứng):

```xml
<meta-data
    android:name="com.google.android.gms.ads.APPLICATION_ID"
    android:value="${admobAppId}" />
```

---

## 6. Mapping Led Banner → SDK

| Led Banner (VTN) | SDK mình |
|---|---|
| `AdsApplication` hooks | `AdsConfig` + `AdsSdk.init` |
| `ConsentHelper.obtainConsentAndShow` | `AdsSdk.consent.obtainAndShow` |
| `loadSplashInterAds2(activity, id, 3000, cb)` | `splash.load(activity, id, 3000, cb)` |
| `onCheckShowSplashWhenFail` | `splash.retryOnResume(...)` |
| `loadAndShowInter(..., force=true, cb)` | `interstitial.loadAndShow(..., ignoreInterval=true, cb)` |
| `loadNativeAd` + `pushAdsToViewCustom` | `native.load` + `native.bind` |
| `loadBannerPlugin(activity, frAds, shimmer, config)` | `banner.load(activity, container, shimmer, BannerConfig)` |
| `AppOpenManager.disableAppResumeWithActivity` | `appOpen.disableResumeWith(clazz)` |
| `getKeyRemoteIntervalShowInterstitial` | `AdsConfig.interstitialIntervalRemoteKey` — xem §2.4 policy RC |
| `logRevenueAdjustWithCustomEvent` | `RevenueLogger` + `FunnelLogger` (`af_inters_*`, `ad_impression`) |
| `AdsConfig.isCheckNativeHome` | **App** (`is_show_*` / `is_load_*`) |
| — (Pirago) Native collap / open-as-inter / resume-as-inter | `native.loadCollapsible`, `OpenAdsConfig`, `ResumeFormat` |

Remote Config placement flags **không** thuộc SDK. SDK có thể expose `RemoteConfigBridge` optional; mặc định app tự `FirebaseRemoteConfig.getBoolean`.

---

## 7. Phase

### Phase 0 — Spec + sample host (1–2 ngày)

- [x] Catalog tính năng (file này).
- [ ] Chốt ad unit test GMA.
- [ ] Demo XML `FrameLayout` + demo Compose `AndroidView` (chưa fill ads).

### Phase 1 — Core GMA owned (1 tuần)

- [ ] `AdsSdk.init` + lifecycle Activity.
- [ ] Consent UMP.
- [ ] Inter load / show / loadAndShow + interval **+ exempt** open/resume.
- [ ] Splash timeout + retry onResume.
- [ ] Open ads: first-open AND + format AOA|Inter.
- [ ] App Open **và** resume-as-Inter + exclude Activity.
- [ ] Rewarded load/show (demo 1 nút).
- [ ] `onNextAction` contract + unit test fake.

**Exit:** XML + Compose: inter test, open ads AOA|Inter, resume AOA|Inter exclude Splash, 1 rewarded.

### Phase 2 — In-layout ads (1–1.5 tuần) — phần quyết định Compose/XML

- [ ] Banner Adaptive vào `ViewGroup`.
- [ ] Collapsible **banner** extras + refresh.
- [ ] Native `AdLoader` + `bindNative` (headline, body, CTA, icon, MediaView).
- [ ] Native template Small / Medium / Fullscreen (Next onboarding **ngoài** ad).
- [ ] Native Collapsible → Small + `time_reload_collap_ad` + preload trước Home.
- [ ] `loadBottomAd` swap Native Small / Banner.
- [ ] Destroy / leak: `onRelease` Compose, `onDestroy` XML.
- [ ] Optional `:ads-sdk-compose`.

**Exit:** Led Banner-like Home (native + collapsible banner) **và** Pirago-like Home (native collap hiện ngay) trên **cả** Compose sample và XML sample.

### Phase 3 — Revenue + ProGuard + publish (2–3 ngày)

- [ ] `RevenueLogger` Firebase `ad_impression` (đủ param) + AppsFlyer `af_ad_revenue` (Adjust optional).
- [ ] `FunnelLogger`: `af_inters_ad_eligible` / `api_called` / `displayed` (+ rewarded).
- [ ] `consumer-rules.pro`.
- [ ] `maven-publish` mavenLocal.
- [ ] README: XML vs Compose snippets + RC key mapping Led vs Pirago.

### Phase 4 — Pilot Led Banner (sau v1)

- Thay `vtn_ads_libs` trên branch riêng.
- Giữ `AdsConfig` flags của app.
- So fill: splash, native language/home, collapsible, inter create/back.

### Phase 5 — Optional

- MAX module.
- Compat `com.nlbn.ads.util.Admob`.
- Rewarded / MREC nếu game cần.

---

## 8. Việc không copy từ VTN

| VTN | Lý do |
|---|---|
| `package_apps.json` | License / danh sách app lạ |
| `isLoadFullAds` = DEBUG \|\| !organic | Policy mơ hồ; Led Banner chỉ dùng đổi layout |
| Facebook SDK full vì `setFan()` rỗng | Không |
| Pin GMA trong app thấp hơn POM lib | Led Banner 22.5 vs lib 23.4 — SDK pin GMA, cấm app hạ |
| Billing, RateDialog, SpinKit, Lottie | Ngoài ads |
| `proguard.txt` rỗng | Phải ship consumer rules |

Giữ từ VTN: Application init, UMP, splash timeout, `onNextAction`, native bind custom view, banner vào `ViewGroup`, AOA exclude Activity, paid log.

---

## 9. Rủi ro

1. **JBase AAR không dùng được** nếu cấm UnityPlayer — đừng Phase-0 “typed wrapper AAR” rồi mới phát hiện banner không vào Compose slot.
2. Native custom là gap lớn nhất (JBase AdMob không có) — phải viết GMA.
3. Collapsible overlay: Compose không reflow; document rõ.
4. Hilt vs `AdsApplication`.
5. Memory: `NativeAd` leak nếu cache static như Led Banner `AdsConfig` — SDK TTL + destroy.
6. Policy: splash + AOA + collapsible + native dày — SDK không bật hết mặc định.
7. Consent trước `AppsFlyer.start()` / load ads.
8. RC: hai lần `fetchAndActivate` (SDK + Splash) — mặc định `ReadOnly` nếu app đã fetch.
9. Native collap ≠ banner collapsible — nhầm API sẽ fail brief Pirago.
10. Next onboarding nằm trong `NativeAdView` → click Next = click ads (policy + UX).
11. Interval exempt sai → đối tác báo “không show resume/open vì dính 15s”.
12. Funnel AF thiếu `eligible` trước khi show → đối tác so Fill/eligible trên AppsFlyer lệch.

---

## 10. Definition of done (v1)

- [ ] `implementation("com.yourorg.ads:sdk:1.0.0")` mavenLocal.
- [ ] Không UnityPlayer, không reflection, không `files("*.aar")` JBase.
- [ ] **Cùng API** chạy demo XML và demo Compose: consent → splash/open (AOA|Inter) → banner in-container → native Small/Medium/Full + **native collap** → inter interval (+ exempt) → resume AOA|Inter exclude → rewarded.
- [ ] `onNextAction` không kẹt navigation.
- [ ] Funnel AF + `ad_impression` Firebase bắn đủ param.
- [ ] Consumer ProGuard `minifyEnabled true`.
- [ ] README: init, XML `FrameLayout`, Compose `AndroidView`, `RemoteConfigPolicy`, mapping key Led vs Pirago, test IDs. App flags `is_show_*` / `is_load_*` không nằm trong SDK.

---

## 11. Việc làm tuần đầu (đã chỉnh)

Làm ở `/Users/vu/projects/android/ad-sdk` (repo library), không nhét vào Led Banner.

1. Skeleton `:ads-sdk` + `AdsSdk.init` + lifecycle.
2. Inter + splash + UMP trên **cả** `demo-xml` và `demo-compose`.
3. Banner `ViewGroup` + Compose `AndroidView` cùng container API.
4. Native bind custom layout + template Small/Medium + collap.
5. Open-ads type + resume format + AF funnel.
6. Mới tính pilot Led Banner / app Pirago-like.

Không làm: javap/wrapper AAR JBase, `UnityPlayer` shim, MAX router — trừ khi product đổi lại yêu cầu mediation MAX. AdMob Mediation adapters để app thêm.
