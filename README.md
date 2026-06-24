# JBase Ads Kotlin Demo

Native Android/Kotlin replacement for the Unity ads demo in this repository.

## What This App Does

- Uses the existing JBase Android AARs from `Assets/Plugins/Android`.
- Provides a normal Android demo UI for AdMob, MAX, primary/fallback routing, and hide/show flows.
- Includes a tiny `com.unity3d.player.UnityPlayer` compatibility shim because the existing AARs read `UnityPlayer.currentActivity`.
- Avoids Unity IDE, Unity scenes, and C# code.

## Current AARs Included

- `ads-release.aar`
- `admobadshelper-release.aar`
- `maxads-release.aar`
- `remoteconfig-release.aar`
- `analytic-release.aar`
- `base-release.aar`

`googlemobileads-unity.aar` is intentionally not included. Add it only if runtime testing proves a JBase AAR depends on Google Unity plugin classes.

## Setup

1. Open `AndroidKotlinAdsDemo` in Android Studio.
2. Install Android SDK/Build Tools compatible with AGP `9.2.0`.
3. Copy `local.properties.example` to `local.properties`.
4. Fill real keys if available:
   - `JBASE_ADMOB_APP_ID`
   - `JBASE_APPLOVIN_SDK_KEY`
   - `JBASE_FACEBOOK_APP_ID`
   - `JBASE_FACEBOOK_CLIENT_TOKEN`
   - `JBASE_INCLUDE_RESTRICTED_APPLOVIN_ADAPTERS=true` only if your environment can resolve AppLovin's Vungle and ByteDance MAX adapters.
5. Add a real `app/google-services.json` if Firebase/Remote Config should connect to a real project.

## Build

Use Android Studio, or from this directory:

```bash
gradle assembleDebug
```

The repository includes `gradle/wrapper/gradle-wrapper.properties`, but not `gradle-wrapper.jar`. Generate it with `gradle wrapper` if the team wants committed wrapper scripts.

## Runtime Notes

- The first implementation calls AAR APIs by reflection so the demo can survive incomplete or obfuscated public signatures while the team audits the binary libraries.
- Logs in the UI show which methods are missing or failing.
- Empty ad unit IDs are used until real app config is supplied.
- Native ads are best-effort because the Unity project routed them through `AndroidBridge`, but `com.jb.lib.AndroidBridge` was not present in the inspected AARs.
- AppLovin Vungle and ByteDance MAX adapters are disabled by default because AppLovin's public Maven endpoint returns `403 Forbidden` for the pinned versions in some environments. Keep them disabled for local demo builds, or enable them with `JBASE_INCLUDE_RESTRICTED_APPLOVIN_ADAPTERS=true` after pinning versions your team can resolve.

## Next Hardening Steps

1. Run the app on a device and capture Logcat from init and each ad button.
2. Decompile or obtain source for the JBase AARs and replace reflection calls with typed wrappers.
3. Confirm exact init argument order for AdMob/MAX IDs and move it into a typed config model.
4. Pin all mediation adapter versions to known-good versions from the company's production app.
5. Enable `google-services` and Crashlytics plugins after adding a real `google-services.json`.
