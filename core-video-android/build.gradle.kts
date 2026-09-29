plugins {
    id("com.android.library")
    kotlin("android")
}

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
}

kotlin {
    jvmToolchain(11)
}

dependencies {
    implementation(project(":core"))
    implementation(libs.kermit)
    implementation(libs.kotlinx.coroutines.android)
}
