# Plan: Android Ads SDK (Compose + XML)

Tài liệu này là **plan + lịch sử v1**. Hợp đồng app đích hiện tại: [SDK_INTEGRATION.md](SDK_INTEGRATION.md). Native Themie: [NATIVE_CUSTOM_LAYOUT_ALIAS_FIX.md](NATIVE_CUSTOM_LAYOUT_ALIAS_FIX.md) (**đã ship**, không còn gap ticket).

- Engine tham khảo (không ship binary Unity): `sub-ads-demo` / AAR JBase — chỉ học Init, interval, AOA, UMP, paid-event.
- Consumer tham khảo: Led Banner (`/Users/vu/projects/android/led-banner/ledbanner`) gọi `com.nlbn.ads` / VTN 2.0.0.
- Brief đối tác điển hình: Pirago monet (Dog Translator / Haircut) — placement + RC + native collap + funnel AF + **`mediation_type`**.
- **Không copy** package `com.nlbn.ads`, allowlist `package_apps.json`, hay `isLoadFullAds` organic/paid. **Không** bọc AAR JBase (`maxads-release.aar`, `UnityPlayer`).

## Trạng thái hiện tại (SoT — đọc cái này, không đọc “v1 chỉ AdMob” bên dưới như lệnh)

Maven: `com.pirago.ads-helper` — Pages `https://ka1toz.github.io/pi-ads-helper/`. **Không** dùng `com.yourorg.ads`.

| Artifact | Module | 1.0.2 (Pages) | 1.0.3 (source hiện tại) |
|---|---|---|---|
| `sdk` | `:ads-sdk` | GMA public API | + `AdsSdk.units` / `isMax` / `mrec` |
| `sdk-compose` | `:ads-sdk-compose` | `AndroidView` | + `AdsMrec` |
| `sdk-max` | `:ads-sdk-max` | **Không có** | Optional MAX (pin AppLovin). App thêm nếu hỗ trợ `mediation_type = 0` |

**Đã chốt, đã code, version `1.0.3` trên source.** Public Pages khi `publishAdsSdk` + rsync `gh-pages` (**giữ** 1.0.2).

- Dual-engine **trong APK**. Firebase `mediation_type`: `0` = MAX, `1` = AdMob Mediation, default **1**.
- Một mediator / session. Đổi RC chỉ có hiệu lực cold start sau.
- MAX **đúng 4** ad unit: Inter, AOA, Banner, MREC. Không native MAX, không rewarded MAX.
- Native medium/small/collap/after-inter = AdMob only. MREC = MAX only. Inter/AOA/banner = cả hai.
- Placement flags (`is_show_inter_*`, `is_show_mrec_*`, …) **ở app**. SDK đọc knob global (tên key qua `AdsConfig`).
- Haircut: `aoa_type` / `resume_type` = chuỗi `inter` \| `aoa` \| rỗng → field **`formatRemoteKey`** / **`resumeTypeRemoteKey`**. Không nhét chuỗi vào field boolean Led Banner.
- Kids/Families: không init MAX.
- Demo `:demo-xml` / `:demo-compose` **không** depend `:ads-sdk-max` (ở lại GMA).

Phase 0–4 (GMA ViewGroup, UMP, native collap, publish 1.0.2) **đã xong**. Phase 5 MAX module **đã code**, version source **1.0.3**; Pages còn 1.0.2 cho đến khi rsync `gh-pages`.

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

**Kết luận (v1, đã giữ):** hình API consumer (VTN-style) hợp lý. Hình **engine = AAR JBase** không hợp lý. Core viết **GMA owned** trong `:ads-sdk`. AAR JBase chỉ là tài liệu hành vi.

**Cập nhật sau v1:** MAX **không** nằm trong artifact `sdk` mặc định. Module optional `:ads-sdk-max` (`sdk-max`) + reflection loader. AdMob Mediation adapters vẫn **app thêm**. Không wrap JBase MAX AAR.

---

## 1. Mục tiêu

1. Artifact Gradle: `com.pirago.ads-helper:sdk:<version>` (+ optional `sdk-compose`, `sdk-max`) — app `implementation`, gọi API typed.
2. Host **XML View** và **Jetpack Compose** cùng một API. `:ads-sdk` **không** phụ thuộc Compose. Compose chỉ `AndroidView` bọc `ViewGroup`.
3. Core GMA owned. Không Unity, không `UnityPlayer`, không bọc AAR JBase. Optional MAX qua `:ads-sdk-max` (không pin vào `sdk`).
4. Đủ port Led Banner + Pirago: splash/open, resume, banner/native vào container, native custom XML (Themie alias), inter interval, UMP, `mediation_type`.
5. Paid-event pluggable (Firebase + AppsFlyer hoặc Adjust).

### Không làm (vẫn cấm)

- Pin MAX / adapter mạng (Meta, Mintegral, …) vào artifact `sdk` mặc định. App tự thêm adapter. `sdk-max` chỉ pin AppLovin core.
- Redistribute 6 AAR JBase obfuscated / `UnityPlayer`.
- Clone `com.nlbn.ads` drop-in.
- IAP tắt ads, rate dialog, `isLoadFullAds` organic.
- Hard-code “show ads everywhere” — policy do app + Remote Config app.
- Native / rewarded MAX; ad unit MAX thứ 5.
- Init MAX + AdMob Mediation cùng session.

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
| Native Fullscreen onboarding chrome | Layout full + chỗ **Next của app** ngoài `NativeAdView`. Countdown close **app** (option A). Không `showFullscreen` SDK |
| MREC vào `ViewGroup` | **Đã có** `AdsSdk.mrec` — **MAX only**. AdMob không load MREC |
| Preload queue native (1 backup) | Led Banner reload khi `onAdImpression`; collap Home cần sẵn ad |
| Loading dialog khi `loadAndShowInter` | VTN có; default **off** |
| AdMob Mediation adapters | App tự thêm; dùng khi `mediation_type = 1` |
| Module `:ads-sdk-max` | Optional; `mediation_type = 0`. 4 format. Loader reflection từ `:ads-sdk` |
| `AdsApplication` base class | Optional — **ưu tiên** `AdsSdk.init` từ `Application.onCreate` (Koin) |
| Module `:ads-sdk-compose` | `AdsBanner` / `AdsNative` / `AdsBottom` / `AdsMrec` = `AndroidView` — **không** bắt buộc |

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
| `interval_show_interstitial` | `ads_interval` (number) | Cooldown inter content. Haircut default brief **10**. Exempt open/resume |
| `cb_fetch_interval` | `time_reload_collap_ad` / `time_reload_native_collap` | Reload collapsible — **banner** (Led) vs **native collap** (Pirago) |
| `show_resum_ads` (boolean) | `resume_type` (string `inter`\|`aoa`\|rỗng) | Led: `resumeRemoteKey`. Haircut: **`resumeTypeRemoteKey`**. Rỗng = tắt |
| `show_open_ads` + `show_opens_ads_type` (boolean) | `aoa_type` (string) + `show_aoa_first_open` | Led: `enabledRemoteKey` + `typeIsInterRemoteKey`. Haircut: **`formatRemoteKey`**. Rỗng = tắt |
| — | `resume_ads_interval` | Giãn cách resume; **không** dùng `ads_interval` |
| — | `mediation_type` (0 MAX / 1 AdMob, default 1) | SDK đọc qua `mediationRemoteKey` |
| — | `is_show_native_small` | Swap Native Small / Banner — **AdMob**. MAX: MREC cùng slot |
| optional JSON `configKey` banner | — | Type + refresh — chưa làm |

SDK đọc thêm `mediation_type` (qua `mediationRemoteKey`). **Không** đọc `is_show_inter_*` / `is_show_native_*` / `is_show_mrec_*` / `is_load_*` (placement). **Không** đọc `rating_popup`.

`AdsSdk.init` phải **await** fetch (hoặc nhận `Task`) trước splash load — Led Banner splash timeout 30s vừa UMP vừa RC; SDK document: consent + RC ready rồi mới `loadSplashInter` / open ads.

Compose/XML: RC không ảnh hưởng UI.

### 2.5 Brief monet đối tác (Pirago_Dog Translator_Monet.xlsx) — gap vs plan cũ

Nguồn: sheet **AdRemoteEvent** + **Feedback** của `Pirago_Dog Translator_Monet.xlsx` (app Dog Translator). Đây là **kịch bản giao việc điển hình**, không phải spec Led Banner. Plan cũ chỉ cover VTN/Led Banner → thiếu vài capability SDK hay bị đối tác bắt khi nghiệm thu.

#### Brief yêu cầu gì (rút pattern)

| Layer | Pattern trong Excel | SDK hay App |
|---|---|---|
| Header | “Sử dụng **Admob Mediation**” và/hoặc Haircut `mediation_type` | Console + adapter **app**; MAX = optional `sdk-max`, không JBase AAR |
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
11. **AdMob Mediation** = GMA + adapter khi session = `1`. **MAX** = `sdk-max` khi session = `0`. Không cùng lúc.
12. **Haircut string RC** (`aoa_type` / `resume_type`) song song boolean Led Banner — field `AdsConfig` khác nhau, xem SDK_INTEGRATION §5.1.

#### Logic RC open ads

**Led Banner (boolean, default SDK):**  
Lần mở **đầu**: show chỉ khi `show_open_ads_first_open == true` **và** `show_open_ads == true`.  
Lần **2+**: chỉ `show_open_ads`.  
Format: `show_opens_ads_type == true` → Inter, `false` → AOA.

**Pirago / Haircut (chuỗi):**  
`aoa_type` = `inter` \| `aoa` \| rỗng (rỗng = tắt). First open: `show_aoa_first_open` AND type ≠ rỗng. Gán `OpenAdsConfig.formatRemoteKey`, **không** `enabledRemoteKey`.

Resume Led: `show_resum_ads` boolean + `ResumeFormat`. Resume Haircut: `resume_type` chuỗi qua **`resumeTypeRemoteKey`**. Không ăn `ads_interval`; dùng `resume_ads_interval`.

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
App Compose┘         │             AdsSdk.units / isMax / mrec (no-op nếu không max)
                     ├── UMP
                     ├── Inter / Splash / Reward / AOA / Resume
                     ├── Banner + Native (templates, collap → small) — AdMob
                     ├── MREC — MAX only
                     └── RevenueLogger + FunnelLogger
                            │
                     GMA + UMP + (optional) Firebase Analytics
                            │
           optional :ads-sdk-max  (AppLovin; reflection từ MaxBridgeLoader)
```

MAX **không** nằm trong AAR `sdk`. App thêm `sdk-max` + 4 ID + `applovin.sdk.key` nếu hỗ trợ nhánh `0`.

Hilt/Koin: `AdsSdk.init(this, config)` trong `onCreate()` là API chính. App đích dùng **Koin**.

```
ad-sdk/
  ads-sdk/                 # Android library, minSdk 24 — artifact sdk
  ads-sdk-compose/         # optional, api(ads-sdk) + compose
  ads-sdk-max/             # optional, api(ads-sdk) + applovin-sdk — artifact sdk-max
  demo-xml/                # sample ViewBinding (GMA; không depend sdk-max)
  demo-compose/            # sample setContent (GMA)
  app/                     # legacy JBase — không ship
```

Publish: `com.pirago.ads-helper:sdk` + `sdk-compose` + `sdk-max`. `./gradlew publishAdsSdk`.

---

## 5. Public API (sketch — đối chiếu code `AdsConfig.kt`)

```kotlin
object AdsSdk {
    fun init(application: Application, config: AdsConfig)
    val isMax: Boolean
    val units: ResolvedAdUnits
    val consent: ConsentController
    val interstitial: InterstitialAds
    val splash: SplashAds
    val banner: BannerAds
    val native: NativeAds
    val appOpen: AppOpenAds
    val rewarded: RewardedAds
    val mrec: MrecAds
}

data class AdsConfig(
    val debug: Boolean = false,
    val interstitialIntervalSec: Int = 15,
    val interstitialIntervalRemoteKey: String? = "interval_show_interstitial", // Haircut: "ads_interval"
    val enableResumeAds: Boolean = true,
    val resumeRemoteKey: String? = "show_resum_ads",          // boolean Led Banner
    val resumeTypeRemoteKey: String? = null,                  // Haircut "resume_type"
    val resumeAdsIntervalRemoteKey: String? = "resume_ads_interval",
    val resumeAdUnitId: String? = null,
    val resumeFormat: ResumeFormat = ResumeFormat.AppOpen,
    val openAds: OpenAdsConfig = OpenAdsConfig(),
    val remote: RemoteConfigPolicy = RemoteConfigPolicy.None,
    val admob: AdmobAdUnits? = null,
    val max: MaxAdUnits? = null,                              // 4 key
    val mediationRemoteKey: String? = "mediation_type",
    val mediationDefault: Int = 1,
    val revenueLogger: RevenueLogger? = null,
    val funnelLogger: FunnelLogger? = null,
)

data class OpenAdsConfig(
    val enabledRemoteKey: String? = "show_open_ads",
    val firstOpenRemoteKey: String? = "show_open_ads_first_open",
    val typeIsInterRemoteKey: String? = "show_opens_ads_type",
    val formatRemoteKey: String? = null,                      // Haircut "aoa_type"
    val appOpenAdUnitId: String? = null,
    val interstitialAdUnitId: String? = null,
)

data class MaxAdUnits(
    val interstitial: String = "",
    val appOpen: String = "",
    val banner: String = "",
    val mrec: String = "",
)
```

Mẫu init Haircut đầy đủ: [SDK_INTEGRATION.md](SDK_INTEGRATION.md) §5. Native collap `expandedLayoutRes` / `collapsedLayoutRes`: [NATIVE_CUSTOM_LAYOUT_ALIAS_FIX.md](NATIVE_CUSTOM_LAYOUT_ALIAS_FIX.md).

Manifest app:

```xml
<meta-data
    android:name="com.google.android.gms.ads.APPLICATION_ID"
    android:value="${admobAppId}" />
<!-- nếu hỗ trợ MAX -->
<meta-data
    android:name="applovin.sdk.key"
    android:value="${applovinSdkKey}" />
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

### Phase 0–4 — GMA library (đã xong, đã public 1.0.2)

- [x] Catalog, demo XML + Compose, GMA test units.
- [x] `AdsSdk.init` + lifecycle, UMP, inter + interval exempt, splash, open ads, resume exclude, rewarded.
- [x] Banner/native `ViewGroup`, templates, native collap, bottom slot, `:ads-sdk-compose`.
- [x] Native Themie alias + `expandedLayoutRes` (option A).
- [x] Revenue/funnel hooks, `consumer-rules.pro`, `publishAdsSdk` → Pages `com.pirago.ads-helper`.

**Exit v1:** demo XML + Compose GMA; `onNextAction`; không Unity/JBase.

### Phase 4 — Pilot app đích

- Themie / Haircut consume artifact (không sửa source SDK trong app).
- So fill: splash/open, native language/home collap, inter, MAX MREC nếu app có `sdk-max`.

### Phase 5 — Optional MAX (code + version 1.0.3)

- [x] Module `:ads-sdk-max`, 4 format, `mediation_type`, `AdsSdk.units` / `isMax` / `mrec`.
- [x] Compose `AdsMrec`.
- [x] `adsSdk.version=1.0.3` + README `sdk-max`.
- [ ] `publishAdsSdk` + đẩy `gh-pages` **giữ** 1.0.2.
- [ ] Demo hoặc app đích verify fill MAX trên device (không bắt buộc compile).
- [ ] Compat `com.nlbn.ads.util.Admob` — **chưa làm**, không block 1.0.3.

Không làm: wrap JBase MAX AAR / `UnityPlayer`.

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
13. Haircut: gán `resume_type` vào `resumeRemoteKey` (boolean) → SDK đọc sai, resume gãy.
14. Init MAX + AdMob Mediation cùng process; hoặc thiếu `sdk-max` khi RC = 0.
15. Native XML trên nhánh MAX (phải MREC).

---

## 10. Definition of done

### v1 (1.0.2 Pages) — đạt

- [x] `implementation("com.pirago.ads-helper:sdk:1.0.2")` GitHub Pages.
- [x] Không UnityPlayer, không `files("*.aar")` JBase trong library ship.
- [x] Demo XML + Compose GMA: consent → open → banner/native collap → inter interval → resume exclude → rewarded.
- [x] `onNextAction` không kẹt navigation.
- [x] Consumer ProGuard.
- [x] Native Themie alias + collap `layoutRes`.

### 1.0.3 — source sẵn; Pages khi rsync

- [x] Code `:ads-sdk-max` + `mediation_type` + 4 format + Haircut string RC.
- [x] Docs: SDK_INTEGRATION / WRAP / NATIVE_FIX / README khớp field `AdsConfig`.
- [x] `adsSdk.version=1.0.3`.
- [ ] `publishAdsSdk` + `gh-pages` giữ 1.0.2.
- [ ] (Nên) fill MAX trên device với SDK key + 4 unit thật.

---

## 11. Public 1.0.3 lên GitHub Pages

Repo library: `/Users/vu/projects/android/ad-sdk/sub-ads-demo`.

1. `./gradlew :ads-sdk:test publishAdsSdk`
2. Đẩy source lên branch GitHub; Maven AAR lên **`gh-pages`** (không đẩy source vào `gh-pages`; **giữ** `sdk/1.0.2/`).
3. App đích copy [SDK_INTEGRATION.md](SDK_INTEGRATION.md) rồi bump `implementation` `1.0.3`.

Không làm: javap/wrapper AAR JBase, `UnityPlayer` shim, native/rewarded MAX, init hai mediator cùng session.
