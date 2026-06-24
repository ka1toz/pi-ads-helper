import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use(::load)
    }
}

fun configValue(name: String): String {
    return localProps.getProperty(name)
        ?: providers.gradleProperty(name).orNull
        ?: ""
}

fun configFlag(name: String): Boolean {
    return configValue(name).equals("true", ignoreCase = true)
}

android {
    namespace = "com.jbase.demo"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.jbase.demo"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        manifestPlaceholders["admobAppId"] = configValue("JBASE_ADMOB_APP_ID")
        manifestPlaceholders["applovinSdkKey"] = configValue("JBASE_APPLOVIN_SDK_KEY")
        manifestPlaceholders["facebookAppId"] = configValue("JBASE_FACEBOOK_APP_ID")
        manifestPlaceholders["facebookClientToken"] = configValue("JBASE_FACEBOOK_CLIENT_TOKEN")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt"
            )
        }
    }
}

dependencies {
    implementation(files("libs/ads-release.aar"))
    implementation(files("libs/admobadshelper-release.aar"))
    implementation(files("libs/maxads-release.aar"))
    implementation(files("libs/remoteconfig-release.aar"))
    implementation(files("libs/analytic-release.aar"))
    implementation(files("libs/base-release.aar"))

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.recyclerview:recyclerview:1.4.0")

    implementation("com.google.android.gms:play-services-ads:25.3.0")
    implementation("com.google.android.gms:play-services-ads-identifier:18.2.0")
    implementation("com.google.android.ump:user-messaging-platform:4.0.0")

    implementation(platform("com.google.firebase:firebase-bom:34.0.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-config")
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-messaging")

    implementation("com.appsflyer:adrevenue:6.9.1")
    implementation("com.appsflyer:af-android-sdk:6.12.2")

    implementation("com.google.ads.mediation:fyber:8.4.5.0")
    implementation("com.google.ads.mediation:inmobi:11.2.0.0")
    implementation("com.google.ads.mediation:ironsource:9.4.2.0")
    implementation("com.google.ads.mediation:facebook:6.20.0.0")
    implementation("com.google.ads.mediation:mintegral:17.1.61.0")
    implementation("com.google.ads.mediation:moloco:4.8.0.0")
    implementation("com.google.ads.mediation:pangle:8.0.0.4.0")
    implementation("com.unity3d.ads:unity-ads:4.18.0")
    implementation("com.google.ads.mediation:unity:4.18.0.0")

    implementation("com.applovin:applovin-sdk:13.3.1")
    implementation("com.applovin.mediation:bigoads-adapter:5.5.0.0")
    implementation("com.applovin.mediation:fyber-adapter:8.4.5.0")
    implementation("com.applovin.mediation:google-adapter:25.3.0.0")
    implementation("com.applovin.mediation:google-ad-manager-adapter:25.3.0.0")
    implementation("com.applovin.mediation:inmobi-adapter:10.8.2.0")
    implementation("com.applovin.mediation:ironsource-adapter:9.4.0.0.0")
    implementation("com.applovin.mediation:facebook-adapter:6.20.0.0")
    implementation("com.applovin.mediation:mintegral-adapter:16.9.91.0")
    implementation("com.applovin.mediation:mobilefuse-adapter:1.9.2.0")
    implementation("com.applovin.mediation:mytarget-adapter:5.27.1.0")
    implementation("com.applovin.mediation:yandex-adapter:7.14.1.0")
    implementation("com.squareup.picasso:picasso:2.8")

    if (configFlag("JBASE_INCLUDE_RESTRICTED_APPLOVIN_ADAPTERS")) {
        implementation("com.applovin.mediation:vungle-adapter:7.4.5.0")
        implementation("com.applovin.mediation:bytedance-adapter:7.1.0.5.0")
    }
}
