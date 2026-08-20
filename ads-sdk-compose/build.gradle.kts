plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
    id("maven-publish")
}

android {
    namespace = "com.ads.sdk.compose"
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
        compose = true
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    api(project(":ads-sdk"))
    implementation("androidx.annotation:annotation:1.9.1")
    api(platform("androidx.compose:compose-bom:2024.12.01"))
    api("androidx.compose.ui:ui")
    api("androidx.compose.foundation:foundation")
    api("androidx.activity:activity-compose:1.10.1")
}

extra["adsSdk.artifactId"] = "sdk-compose"
extra["adsSdk.displayName"] = "Ads SDK Compose"
apply(from = rootProject.file("gradle/ads-sdk-publish.gradle.kts"))
