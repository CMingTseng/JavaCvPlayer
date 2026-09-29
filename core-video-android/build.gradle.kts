import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

plugins {
    id("com.android.library")
    kotlin("android")
    `maven-publish`
}

group = "idv.neo.ffmpeg.media.player"
version = "1.0-SNAPSHOT"

android {
    namespace = "idv.neo.ffmpeg.media.player.core.video.android"
    compileSdk = 36
    defaultConfig {
        minSdk = 21
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

kotlin {
    jvmToolchain(11)
}

dependencies {
    implementation(project(":core"))
    implementation(libs.kermit)
    implementation(libs.kotlinx.coroutines.android)
}

configure<PublishingExtension> {
    publications {
        register<MavenPublication>("release") {
            afterEvaluate {
                from(components["release"])
            }
        }
    }
}
