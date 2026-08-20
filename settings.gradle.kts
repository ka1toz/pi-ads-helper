pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://android-sdk.is.com")
        maven("https://dl-maven-android.mintegral.com/repository/mbridge_android_sdk_oversea")
        maven("https://artifact.bytedance.com/repository/pangle")
        maven("https://artifacts.applovin.com/android")
    }
}

rootProject.name = "JBaseAdsKotlinDemo"
include(":ads-sdk")
include(":ads-sdk-compose")
include(":demo-xml")
include(":demo-compose")
include(":app")
