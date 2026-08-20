plugins {
    id("com.android.application") version "9.2.0" apply false
    id("com.android.library") version "9.2.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.10" apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
    id("com.google.firebase.crashlytics") version "3.0.2" apply false
}

tasks.register("publishAdsSdk") {
    group = "publishing"
    description = "Publish :ads-sdk and :ads-sdk-compose to mavenLocal + build/maven-repo"
    dependsOn(":ads-sdk:publish", ":ads-sdk-compose:publish")
    doLast {
        // GitHub Pages + Jekyll would skip some paths without this file.
        rootProject.layout.buildDirectory.file("maven-repo/.nojekyll").get().asFile.writeText("")
    }
}

tasks.register("exportAdsSdkAar") {
    group = "publishing"
    description = "Copy release AARs into build/dist/"
    dependsOn(":ads-sdk:exportAar", ":ads-sdk-compose:exportAar")
}
