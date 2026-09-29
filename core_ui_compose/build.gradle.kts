import org.gradle.kotlin.dsl.implementation
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.compose.compiler)
    `maven-publish`
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
            compileOnly(libs.kotlin.stdlib)
            compileOnly(libs.kotlinx.serialization.core)
            compileOnly(libs.kotlinx.serialization.json)
            compileOnly(libs.kotlinx.coroutines.core)
            compileOnly(libs.jetbrains.compose.foundation)
            compileOnly(libs.jetbrains.compose.ui)
            implementation(libs.kermit)
//            compileOnly(project(":lib-common-lite"))
            compileOnly("com.github.cybernhl.media:lib-common-lite:727538c430")
            api(project(":core"))
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }

        jvmMain.dependencies {
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.org.bytedeco.javacv.platform)
            implementation(libs.org.bytedeco.ffmpeg.platform.gpl)
            api(project(":core-video-skia"))
        }
        androidMain.dependencies {
            api(project(":core-video-android"))
//            val media3Version = "1.8.0" // 或者您確認的確切版本
//            implementation("androidx.media3:media3-exoplayer:$media3Version")
//            implementation("androidx.media3:media3-ui:$media3Version") // 如果用到 UI 模塊
//            implementation("androidx.media3:media3-common:$media3Version") // common 模塊包含了 Player 接口
//            implementation("androidx.media3:media3-datasource-okhttp:$media3Version") // 如果用到
            // ... 其他 Media3 模塊

//            implementation(libs.org.bytedeco.javacv.platform)
//            implementation(libs.org.bytedeco.ffmpeg.platform.gpl)
//            implementation(libs.org.bytedeco.opencv.platform.gpu)
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