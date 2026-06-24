# AAR Audit Notes

## Findings From The Unity Project

- Unity called `com.jb.lib.AndroidBridge`, but that class was not present in the inspected AARs.
- `admobadshelper-release.aar` exposes `com.jb.admobadshelper.AdmobHelper` and AdMob controllers.
- `maxads-release.aar` exposes `com.jb.maxads.MaxAdsService` and implements the common `com.jb.ads.IAdsService` API.
- Both AdMob and MAX AARs reference `com.unity3d.player.UnityPlayer.currentActivity`.
- `ads-release.aar` exposes `com.jb.ads.IAdsService`, `AdsManager`, and `AdsEventListener`.
- `remoteconfig-release.aar` includes the default keys copied into `app/src/main/res/xml/remote_config_defaults.xml`.

## Compatibility Decisions

- The demo provides `com.unity3d.player.UnityPlayer` as a shim with a mutable `currentActivity`.
- AAR methods are called by reflection until source or decompiled signatures are confirmed.
- Callback handling uses a dynamic proxy for `AdsEventListener`.
- Native ads are best-effort because the missing `AndroidBridge` class likely wrapped additional native-ads-specific APIs.

## What To Confirm On Device

- Whether `AdmobHelper.Init(Activity, String[])` accepts the empty demo args or requires a specific ordered list of ad unit IDs.
- Exact MAX `Init(Activity, String[])` argument order.
- Whether banner/MREC views attach to the Activity decor automatically.
- Whether AdMob native fullscreen/banner/MREC APIs are accessible without `com.jb.lib.AndroidBridge`.
- Whether `googlemobileads-unity.aar` is still required at runtime.
