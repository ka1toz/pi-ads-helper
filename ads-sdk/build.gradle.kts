plugins {
    id("com.android.library")
    id("maven-publish")
}

android {
    namespace = "com.ads.sdk"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }

    buildFeatures {
        buildConfig = true
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    api("com.google.android.gms:play-services-ads:23.6.0")
    api("com.google.android.ump:user-messaging-platform:4.0.0")

    implementation("androidx.annotation:annotation:1.9.1")
    api("androidx.lifecycle:lifecycle-process:2.8.7")
    api("androidx.lifecycle:lifecycle-common:2.8.7")

    // Runtime of AdsSdk.remoteConfig — must ship as a transitive for AAR/Maven consumers.
    api("com.google.firebase:firebase-config:23.0.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.google.truth:truth:1.4.4")
}

extra["adsSdk.artifactId"] = "sdk"
extra["adsSdk.displayName"] = "Ads SDK"
apply(from = rootProject.file("gradle/ads-sdk-publish.gradle.kts"))
