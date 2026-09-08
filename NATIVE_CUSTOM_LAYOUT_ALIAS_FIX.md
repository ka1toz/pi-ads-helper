# AdsSdk custom-layout / chrome compat — Themie / Pirago

> Handoff **đã ship** (native AdMob). App tiêu thụ: **11-Themie** (`jp.pirago.theme.icon`) và app Pirago khác.
>
> **Không chỉ native in-layout.** Collapsible, fullscreen native, bottom slot, và alias ID
> (`native_ad_*` **và** `adAppIcon`) đã có trong `:ads-sdk`. Banner / interstitial / rewarded / AOA / MREC
> dùng UI Google hoặc MAX — **không** custom XML.
>
> Native custom XML **chỉ nhánh AdMob**. Nhánh MAX (`mediation_type = 0`) dùng MREC, không native.
> Consumer: [SDK_INTEGRATION.md](SDK_INTEGRATION.md) mục 7.1.

**Trạng thái:** Option **A** (alias + `layoutRes` / `expandedLayoutRes`) đã có trong code. Option **B** (`showFullscreen` + countdown `btnCloseAd` trong SDK) **chưa làm** — close countdown vẫn **app-owned**.

---

## 1. Phân loại — cái nào SDK đã làm

| Format | UI custom được? | Việc SDK | Trạng thái |
|---|---|---|---|
| Native in-layout (Small / Medium / Language) | Có — `loadAndBind(..., layoutRes)` | Alias `native_ad_*` + CTA `ViewGroup` | **Ship** |
| Native collapsible (Home bottom) | Có — chrome SDK + XML app cho slot | `expandedLayoutRes` / `collapsedLayoutRes` | **Ship** |
| Native fullscreen | Có — template hoặc `layoutRes` | Alias `adAppIcon`. Countdown `btnCloseAd` **app** | **Ship A** (không B) |
| Bottom slot (`AdsSdk.bottom`) | Wrapper collap / small / banner | Forward layoutRes xuống collap / small | **Ship** |
| Banner / Inter / Rewarded / AOA / Splash | Không | UI Google | Không đổi layout |
| MREC | Không | MAX `MaxAdView` | Không custom XML native |

Câu “SDK đọc XML fail” chỉ còn đúng nếu app **thiếu headline** hoặc nhầm GMA collapsible banner với native collap.

---

## 2. Native in-layout — alias ID + CTA ViewGroup (đã làm)

SDK inflate XML rồi `NativeAssetBinder.populate`. Thiếu headline → log

```text
Native bind missing headline view — ad will not display
```

Files:

- `ads-sdk/.../nativead/NativeAssetBinder.kt`
- `ads-sdk/src/main/res/values/ids.xml`
- `ads-sdk/.../nativead/NativeAds.kt` (`bind(layoutRes)`)

### ID SDK nhận

Canonical `ads_sdk_*` + DIY (`adHeadline`, …) + Google (`ad_headline`, …) + Pirago/Themie (`native_ad_*`, `adAppIcon`). Demo `layout_native_ad_custom.xml` và fixture Pirago trong `:demo-xml` / `:demo-compose` khớp.

| Asset | App ID Themie | SDK |
|---|---|---|
| NativeAdView | `native_ad_view` / `nativeAdView` | Nested lookup |
| Headline | `native_ad_headline` | Alias |
| Body | `native_ad_body` | Alias |
| CTA | `native_ad_call_to_action` (thường `FrameLayout`) | `callToActionView` = container |
| CTA label | `native_ad_call_to_action_text` | Set text nếu CTA là `ViewGroup` |
| Icon | `native_ad_icon` / `adAppIcon` | Alias |
| Media | `native_ad_media` | Alias |
| AdChoices | `ad_choices_view` | Alias (`ad_choices_container` cũng có) |
| Content / loading | `native_ad_content_root`, `native_loading_root` | Sau bind: VISIBLE / GONE |
| Blur | `adMediaBackground` | **App UX** — SDK không port Coil |

---

## 3. Native fullscreen — Option A

Themie: `layout_native_ad_fullscreen.xml`.

- Nested `NativeAdView` id `nativeAdView` — tìm được.
- Headline/body/CTA: `adHeadline`, `adBody`, `adCallToAction` — alias.
- Icon: **`adAppIcon`** — alias (cùng list với `adIcon` / `native_ad_icon`).
- `AdChoicesView` không có id → SDK không gán `adChoicesView` (GMA vẫn có thể overlay). Khuyên app gắn `@+id/ad_choices_view`.
- Overlay app: `btnCloseAd` (countdown), `native_loading_root`, `adMediaBackground`. Template SDK `NativeTemplate.Fullscreen` **không** có countdown.

**A (đã ship):** `loadAndBind(..., R.layout.layout_native_ad_fullscreen)` bind asset đúng. Close countdown **vẫn app**.

**B (chưa làm):** `AdsSdk.native.showFullscreen(...)` inflate overlay + countdown `btnCloseAd`. Không có API này — đừng gọi.

---

## 4. Native collapsible / bottom — layoutRes app

Themie `layout_native_ad_collapsible.xml` = **một** `NativeAdView` medium-like, không có 2 slot chrome SDK.

`NativeCollapsibleController` vẫn inflate chrome SDK `ads_native_collapsible` (expanded slot, nút collapse, collapsed slot). App truyền XML **vào slot**, không thay chrome:

```kotlin
data class NativeCollapConfig(
    val expandedUnitId: String,
    val collapsedUnitId: String = expandedUnitId,
    // …
    @LayoutRes val expandedLayoutRes: Int = 0,   // 0 = NativeTemplate.Medium
    @LayoutRes val collapsedLayoutRes: Int = 0,  // 0 = NativeTemplate.Small
)
```

Chrome XML **phải** có (default SDK đã có): `ads_sdk_collap_expanded`, `ads_sdk_collap_collapsed`, `ads_sdk_collapse`.

**Cách dùng Themie (đã chốt):** `expandedLayoutRes = R.layout.layout_native_ad_medium` (hoặc collapsible XML như content), `collapsedLayoutRes = R.layout.layout_native_ad_small`. Không bắt app tách `layout_native_ad_collapsible` thành 2 FrameLayout.

`BottomAdConfig` forward `expandedLayoutRes` / `collapsedLayoutRes`; static small dùng `nativeLayoutRes`. Compose `AdsNativeCollapsible` / `AdsBottom` cùng surface.

**Không** truyền `layout_native_ad_collapsible` làm toàn bộ chrome — thiếu `ads_sdk_collap_expanded` / nút collapse.

---

## 5. Banner / Inter / Rewarded / AOA / Splash / MREC

Không custom layout XML native.

- Banner: `BannerConfig` + `FrameLayout` app.
- Inter / rewarded / AOA / splash: Activity + callback.
- MREC: `AdsSdk.mrec` / Compose `AdsMrec` — **MAX only**.

**Không** nhầm GMA collapsible banner (`BannerType.CollapsibleBottom`) với native collap.

---

## 6. Acceptance (đã đạt trên SDK)

### In-layout native

- XML `native_ad_*` + CTA FrameLayout: headline bound, CTA text trên nested TextView.
- XML DIY `adHeadline` / template `ads_sdk_*`: không regress.

### Fullscreen native

- XML Themie fullscreen: headline + icon `adAppIcon` bound.
- Close countdown **không** thuộc SDK.

### Collapsible / bottom

- `loadCollapsible` / `bottom.load` bind app medium + small XML khi truyền layoutRes.
- Chrome default SDK vẫn chạy khi `layoutRes = 0`.

### Không regress

- Demo XML/Compose: banner, inter, rewarded, AOA, splash, DIY custom native, fixture Pirago.

Unit test `NativeAssetBinderTest.kt` **chưa** có — verify bằng demo fixture + `:ads-sdk:test` (config mediation), không phải binder XML.

---

## 7. App Themie (consume artifact) — không làm trong PR SDK

| Slot app (`AdHelper` / `NativeAdSlot`) | Gọi AdsSdk |
|---|---|
| MEDIUM / IN_LIST | `loadAndBind(..., R.layout.layout_native_ad_medium)` |
| SMALL | `loadAndBind(..., R.layout.layout_native_ad_small)` |
| LANGUAGE / ONBOARDING | `loadAndBind(..., R.layout.layout_native_medium_language)` |
| COLLAPSIBLE / Home bottom | `bottom.load` / `loadCollapsible` + `expandedLayoutRes` / `collapsedLayoutRes` |
| FULLSCREEN | `loadAndBind` fullscreen XML; countdown `btnCloseAd` app |
| Banner | `AdsSdk.banner.load` |
| Inter / rewarded / splash / resume | API fullscreen — không đổi XML |
| MAX session | **Không** native; MREC cùng host |

Blur `adMediaBackground` + chrome close: app.

---

## 8. Non-goals (vẫn giữ)

- Rename canonical `ads_sdk_*`.
- Custom UI interstitial / rewarded / AOA / banner / MREC.
- Port Themie Coil blur / fullscreen gate vào SDK (option B).
- Native trên nhánh MAX.

---

## 9. Verify

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
5. `AdsSdk.isMax == true` → không gọi native / bottom collap.
