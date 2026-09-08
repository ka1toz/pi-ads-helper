plugins {
    id("com.android.library")
    id("maven-publish")
}

android {
    namespace = "com.ads.sdk.max"
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

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    api(project(":ads-sdk"))
    api("com.applovin:applovin-sdk:13.3.1")
}

extra["adsSdk.artifactId"] = "sdk-max"
extra["adsSdk.displayName"] = "Ads SDK MAX"
apply(from = rootProject.file("gradle/ads-sdk-publish.gradle.kts"))
