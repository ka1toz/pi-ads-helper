import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.register
import java.net.URI

val adsGroup = providers.gradleProperty("adsSdk.group").getOrElse("com.pirago.ads-helper")
val adsVersion = providers.gradleProperty("adsSdk.version").getOrElse("1.0.3")
val adsScmUrl = providers.gradleProperty("adsSdk.scmUrl")
    .getOrElse("https://github.com/ka1toz/pi-ads-helper")
val adsArtifact = extra["adsSdk.artifactId"] as String
val adsName = extra["adsSdk.displayName"] as String

group = adsGroup
version = adsVersion

afterEvaluate {
    extensions.configure<PublishingExtension> {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = adsGroup
                artifactId = adsArtifact
                version = adsVersion
                pom {
                    name.set(adsName)
                    description.set("AdMob ads SDK for Android (XML + optional Compose).")
                    url.set(adsScmUrl)
                    scm {
                        url.set(adsScmUrl)
                        connection.set("scm:git:git://github.com/ka1toz/pi-ads-helper.git")
                        developerConnection.set("scm:git:ssh://git@github.com/ka1toz/pi-ads-helper.git")
                    }
                }
            }
        }
        repositories {
            mavenLocal()
            maven {
                name = "BuildDir"
                url = uri(rootProject.layout.buildDirectory.dir("maven-repo"))
            }
            val remoteUrl = providers.gradleProperty("adsSdk.mavenUrl").orNull
                ?: System.getenv("ADS_SDK_MAVEN_URL")
            if (!remoteUrl.isNullOrBlank()) {
                maven {
                    name = "Remote"
                    url = URI(remoteUrl)
                    credentials {
                        username = providers.gradleProperty("adsSdk.mavenUser").orNull
                            ?: System.getenv("ADS_SDK_MAVEN_USER")
                            ?: ""
                        password = providers.gradleProperty("adsSdk.mavenPassword").orNull
                            ?: System.getenv("ADS_SDK_MAVEN_PASSWORD")
                            ?: ""
                    }
                }
            }
        }
    }

    tasks.register<Copy>("exportAar") {
        group = "publishing"
        description = "Copy the release AAR into build/dist/"
        dependsOn("assembleRelease")
        from(layout.buildDirectory.file("outputs/aar/${project.name}-release.aar"))
        into(rootProject.layout.buildDirectory.dir("dist"))
        rename { "${adsArtifact}-${adsVersion}.aar" }
    }
}
