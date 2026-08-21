# AdsSdk custom-layout / chrome compat — Themie / Pirago

> Handoff cho AI agent trong repo AdsSdk (`sub-ads-demo`).
> App tiêu thụ: **11-Themie** (`jp.pirago.theme.icon`).
>
> **Không chỉ native in-layout.** Collapsible, fullscreen native, bottom slot, và alias ID
> (`native_ad_*` **và** `adAppIcon`) cũng lệch. Banner / interstitial / rewarded / AOA
> dùng UI Google — **không** cần custom XML; đừng “sửa layout” cho các format đó.

---

## 1. Phân loại — cái nào thật sự cần sửa SDK

| Format | UI custom được? | Gap với Themie | Việc SDK |
|---|---|---|---|
| Native in-layout (Small / Medium / Language) | Có — `loadAndBind(..., layoutRes)` | ID `native_ad_*` + CTA `ViewGroup` | Alias + CTA bind |
| Native collapsible (Home bottom) | Có — **nhưng API chưa nhận layout app** | SDK luôn inflate `ads_native_collapsible` + `NativeTemplate.Medium/Small` | Truyền `layoutRes` expanded/collapsed (+ chrome) |
| Native fullscreen | Có — template hoặc `layoutRes` | Layout app DIY + overlay close countdown; icon id `adAppIcon` | Alias `adAppIcon`; optional fullscreen host/chrome API |
| Bottom slot (`AdsSdk.bottom`) | Wrapper collap / small / banner | Cùng collap: không cắm XML app | Forward layoutRes xuống collap / small |
| Banner (adaptive / GMA collapsible) | Không | View Google `AdView` | **Không đổi layout** |
| Interstitial | Không | Fullscreen GMA | **Không đổi layout** |
| Rewarded | Không | Fullscreen GMA | **Không đổi layout** |
| App Open / resume AOA | Không | Fullscreen GMA | **Không đổi layout** |
| Splash inter | Không | Fullscreen GMA | **Không đổi layout** |

Câu “SDK đọc XML fail” chỉ đúng với **native (và collap/fullscreen dùng native XML)**.
Inter/banner/rewarded/AOA **không inflate XML app**.

---

## 2. Native in-layout — lệch ID + CTA ViewGroup

### 2.1 Root cause

SDK inflate XML thành công. Fail ở `NativeAssetBinder.populate`: không tìm headline → log

```text
Native bind missing headline view — ad will not display
```

Files:

- `ads-sdk/.../nativead/NativeAssetBinder.kt`
- `ads-sdk/src/main/res/values/ids.xml`
- `ads-sdk/.../nativead/NativeAds.kt` (`bind(layoutRes)`)

### 2.2 ID SDK đã nhận

Canonical `ads_sdk_*` + DIY (`adHeadline`, …) + Google (`ad_headline`, …).
Demo `layout_native_ad_custom.xml` **đã khớp**.

### 2.3 ID Themie (`layout_native_ad_medium|small|collapsible`, `layout_native_medium_language`)

| Asset | App ID | SDK? |
|---|---|---|
| NativeAdView | `native_ad_view` | Thiếu nested lookup (root `NativeAdView` vẫn OK) |
| Headline | `native_ad_headline` | **Thiếu — fail chính** |
| Body | `native_ad_body` | Thiếu |
| CTA | `native_ad_call_to_action` (thường `FrameLayout`) | Thiếu |
| CTA label | `native_ad_call_to_action_text` | Thiếu + SDK chỉ set text nếu CTA là `TextView` |
| Icon | `native_ad_icon` | Thiếu |
| Media | `native_ad_media` | Thiếu |
| AdChoices | `ad_choices_view` | Thiếu (`ad_choices_container` có) |
| Content / loading / blur | `native_ad_content_root`, `native_loading_root`, `adMediaBackground` | App UX — optional |

### 2.4 Fix (in-layout)

1. `ids.xml`: thêm `native_ad_view`, `native_ad_headline`, `native_ad_body`, `native_ad_call_to_action`, `native_ad_call_to_action_text`, `native_ad_icon`, `native_ad_media`, `ad_choices_view`.
2. `findNativeAdView`: `R.id.native_ad_view`.
3. `findAsset`: thêm alias trên, ưu tiên `ads_sdk_*` → DIY → Google → Pirago.
4. CTA: `callToActionView` = container; nếu `ViewGroup` thì set text vào `native_ad_call_to_action_text`.
5. Optional: `native_ad_content_root` → VISIBLE, `native_loading_root` → GONE sau bind.
6. **Không** port blur Coil vào SDK.

---

## 3. Native fullscreen — ID + chrome, không chỉ Medium

Themie: `layout_native_ad_fullscreen.xml`.

- Nested `NativeAdView` id `nativeAdView` — SDK **đã** tìm được.
- Headline/body/CTA: `adHeadline`, `adBody`, `adCallToAction` — SDK **đã** alias.
- Icon: **`adAppIcon`** — SDK chỉ có `adIcon` / `ads_sdk_icon` / `ad_icon` → **icon trống**.
- `AdChoicesView` **không có id** → SDK không gán `adChoicesView` (AdChoices vẫn có thể hiện nếu GMA tự overlay; nên khuyên app gắn id, SDK thêm alias nếu có).
- Overlay app: `btnCloseAd` (countdown 5s), `native_loading_root`, `adMediaBackground`. SDK `NativeTemplate.Fullscreen` **không** có countdown / delay close.

### 3.1 Fix SDK

1. Alias `adAppIcon` trong `ids.xml` + `NativeAssetBinder` icon list.
2. **API (nếu product cần drop-in Themie):** không bắt `loadAndBind` fullscreen vào một `FrameLayout` host rồi tự quản lý close — hiện app `AdHelper` inflate overlay + gate fullscreen. SDK nên một trong hai:

   **A (tối thiểu):** `loadAndBind(..., R.layout.layout_native_ad_fullscreen)` bind asset đúng (`adAppIcon`). Close countdown **vẫn app**.

   **B (đủ pirago):** `AdsSdk.native.showFullscreen(activity, layoutRes, adUnitId, FullscreenNativeConfig)` với:
   - inflate layout app (wrapper + NativeAdView + `btnCloseAd`)
   - bind assets
   - countdown trên view id cấu hình (`closeViewId` / `btnCloseAd`)
   - `onNextAction` khi close / fail / timeout  
   Default close id: `btnCloseAd` + alias `ads_sdk_close`.

Khuyến nghị: làm **A ngay** (alias); **B** nếu brief yêu cầu SDK sở hữu chrome onboarding/fullscreen.

---

## 4. Native collapsible — gap API, không chỉ ID

Themie: `layout_native_ad_collapsible.xml` = **một** `NativeAdView` medium-like (`native_ad_*`) + `collapsible_slot_top`.

SDK hôm nay:

```kotlin
AdsSdk.native.loadCollapsible(activity, container, NativeCollapConfig, placement)
```

`NativeCollapsibleController` **luôn**:

1. Inflate **`R.layout.ads_native_collapsible`** (chrome SDK: expanded slot, collapse `ImageButton`, collapsed slot).
2. Expanded: `bindTemplate(..., NativeTemplate.Medium)` → layout **SDK**.
3. Collapsed: `NativeTemplate.Small` hoặc `AdsSdk.banner.load`.

→ App **không có chỗ** truyền `layout_native_ad_collapsible` / medium / small XML.
Chỉ alias ID **không đủ** để Home collap nhìn như Themie.

### 4.1 Fix SDK

Mở rộng `NativeCollapConfig` (tên gợi ý):

```kotlin
data class NativeCollapConfig(
    val expandedUnitId: String,
    val collapsedUnitId: String = expandedUnitId,
    // existing reload / collapsedNativeSmall / collapsedBanner ...
    @LayoutRes val chromeLayoutRes: Int = R.layout.ads_native_collapsible,
    @LayoutRes val expandedLayoutRes: Int = /* layoutFor(Medium) */,
    @LayoutRes val collapsedLayoutRes: Int = /* layoutFor(Small) */,
)
```

Chrome XML (app hoặc default SDK) **phải** có:

| Role | Canonical | Alias Themie (nếu app chrome) |
|---|---|---|
| Expanded host | `ads_sdk_collap_expanded` | optional `collapsible_slot_top` **chỉ nếu** app tách slot |
| Collapsed host | `ads_sdk_collap_collapsed` | — |
| Collapse button | `ads_sdk_collapse` | app id nếu có |

**Lưu ý Themie:** collapsible layout hiện **không** tách expanded/collapsed FrameLayout như SDK; đó là **một** native medium. Hai hướng:

1. **Bind expanded = app medium XML** vào `ads_sdk_collap_expanded`; collapsed = app small XML. Chrome giữ SDK. App không cần sửa `layout_native_ad_collapsible` thành 2 slot.
2. App refactor XML cho khớp chrome SDK (không khuyến nghị nếu muốn zero-change app).

Ưu tiên **(1)**: `expandedLayoutRes = R.layout.layout_native_ad_medium` (sau khi alias `native_ad_*` xong), `collapsedLayoutRes = R.layout.layout_native_ad_small`. File `layout_native_ad_collapsible.xml` có thể không dùng nếu chrome SDK + medium/small app đã đủ.

`AdsSdk.bottom.load(BottomAdConfig)` phải **forward** `expandedLayoutRes` / `collapsedLayoutRes` (thêm field vào `BottomAdConfig`).

Compose `AdsNativeCollapsible` / `AdsBottom` cùng surface.

---

## 5. Bottom slot

`BottomAds.load` → collap **hoặc** `NativeTemplate.Small` **hoặc** banner.

Gap: Small path hard-code template SDK.

Fix: `BottomAdConfig` thêm `nativeLayoutRes` / collap layout fields; không đụng banner GMA.

---

## 6. Banner / Inter / Rewarded / AOA / Splash

Không custom layout XML. Việc “khớp Themie” là **API + policy**, không phải ID:

- Banner: `BannerConfig` + container `FrameLayout` app — đã đủ.
- Inter: `loadAndShow` + interval — GetTheme đã dùng.
- Rewarded / AOA / splash: Activity + callback — không inflate native XML.

**Không** thêm alias ID cho các format này. Đừng nhầm GMA **collapsible banner** (`BannerType.CollapsibleBottom`) với **native collap**.

---

## 7. Alias bổ sung (gom một lần trong `ids.xml`)

Ngoài `native_ad_*` mục 2:

```xml
<item name="adAppIcon" type="id" />
<item name="btnCloseAd" type="id" /> <!-- chỉ cần nếu làm fullscreen chrome B -->
```

Icon `findAsset`: `ads_sdk_icon`, `adIcon`, `ad_icon`, `native_ad_icon`, **`adAppIcon`**.

---

## 8. Acceptance criteria

### In-layout native

- XML `native_ad_*` + CTA FrameLayout: headline bound, CTA text trên nested TextView.
- XML DIY `adHeadline` / template `ads_sdk_*`: không regress.

### Fullscreen native

- XML Themie fullscreen: headline + **icon `adAppIcon`** bound.
- (Nếu làm B) close countdown + `onNextAction`.

### Collapsible / bottom

- `loadCollapsible` / `bottom.load` bind **app** medium + small XML, không bắt buộc `NativeTemplate.Medium/Small`.
- Chrome default SDK vẫn chạy demo hiện tại (không truyền layoutRes).

### Không regress

- Demo XML/Compose: banner, inter, rewarded, AOA, splash, DIY custom native.
- Docs: bảng ID + collap/fullscreen layoutRes.

---

## 9. Files SDK

```
ads-sdk/src/main/res/values/ids.xml
ads-sdk/src/main/java/com/ads/sdk/nativead/NativeAssetBinder.kt
ads-sdk/src/main/java/com/ads/sdk/nativead/NativeAds.kt
ads-sdk/src/main/java/com/ads/sdk/nativead/NativeCollapsibleController.kt
ads-sdk/src/main/java/com/ads/sdk/bottom/BottomAds.kt
ads-sdk/src/main/java/com/ads/sdk/AdsConfig.kt          # NativeCollapConfig, BottomAdConfig
ads-sdk-compose/.../AdsCompose.kt                       # nếu có collap/bottom params
ads-sdk/src/test/java/com/ads/sdk/nativead/NativeAssetBinderTest.kt
README.md
SDK_INTEGRATION.md
```

---

## 10. App Themie (sau khi SDK ship) — không làm trong PR SDK

| Slot app (`AdHelper` / `NativeAdSlot`) | Gọi AdsSdk mong muốn |
|---|---|
| MEDIUM / IN_LIST | `loadAndBind(..., R.layout.layout_native_ad_medium)` |
| SMALL | `loadAndBind(..., R.layout.layout_native_ad_small)` |
| LANGUAGE / ONBOARDING | `loadAndBind(..., R.layout.layout_native_medium_language)` |
| COLLAPSIBLE / Home bottom | `bottom.load` / `loadCollapsible` + expanded/collapsed layoutRes |
| FULLSCREEN | `loadAndBind` fullscreen XML **hoặc** `showFullscreen` nếu SDK có |
| Banner | `AdsSdk.banner.load` — không đổi XML |
| Inter / rewarded / splash / resume | API fullscreen GMA — không đổi XML |

Blur `adMediaBackground` + unhide `native_ad_content_root` nếu SDK không làm optional UX.

---

## 11. Non-goals

- Rename canonical `ads_sdk_*`.
- Custom UI interstitial / rewarded / AOA / banner.
- Port Themie Coil blur / fullscreen gate vào SDK trừ khi brief B.
- Nhầm native collap với GMA collapsible banner.

---

## 12. Verify

```bash
./gradlew :ads-sdk:test
./gradlew :demo-xml:assembleDebug
./gradlew :demo-compose:assembleDebug
```

Manual:

1. Custom native Pirago IDs + CTA ViewGroup.
2. Fullscreen XML với `adAppIcon`.
3. Collap với expanded/collapsed **app** layouts.
4. Banner + inter + rewarded + AOA không đổi hành vi.
