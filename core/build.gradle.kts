import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}

group = "idv.neo.ffmpeg.media.player"
version = "1.0-SNAPSHOT"

configurations.all {
    resolutionStrategy.cacheChangingModulesFor(0, "seconds")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    jvm(){
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilations.all {
            compilerOptions.configure {
                jvmTarget.set(JvmTarget.JVM_11)
            }
        }
    }
    androidTarget {
        publishLibraryVariants("release", "debug")
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_11)
                }
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlin.stdlib)
            implementation(libs.kotlinx.serialization.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)

            implementation(libs.jetbrains.compose.runtime)


            // Pure Kotlin Multiplatform dependencies
//            implementation(libs.kotlinx.datetime)
            implementation(libs.kermit)
            compileOnly("com.github.cybernhl.media:lib-common-lite:727538c430")
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }

        jvmMain.dependencies {
            compileOnly(libs.kotlinx.coroutines.swing)
            compileOnly(libs.org.bytedeco.javacv.platform)
//            compileOnly(libs.org.bytedeco.ffmpeg.platform.gpl)
//            implementation(libs.org.bytedeco.opencv.platform.gpu)
        }
        androidMain.dependencies {
            compileOnly(libs.kotlinx.coroutines.android)
        }
    }
}

android {
    namespace = "idv.neo.ffmpeg.media.player"
    compileSdk = 36
    defaultConfig {
        minSdk = 21
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}