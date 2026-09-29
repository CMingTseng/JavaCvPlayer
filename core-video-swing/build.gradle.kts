import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    `maven-publish`
}

group = "idv.neo.ffmpeg.media.player"
version = "1.0-SNAPSHOT"

kotlin {
    jvm {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilations.all {
            compilerOptions.configure {
                jvmTarget.set(JvmTarget.JVM_11)
            }
        }
    }
    sourceSets {
        val jvmMain by getting {
            dependencies {
                api(project(":core"))
//                implementation(project(":lib-common-lite"))
                implementation("com.github.cybernhl.media:lib-common-lite:727538c430")
                implementation(libs.kermit)
                implementation(libs.kotlinx.coroutines.swing)
                compileOnly(libs.org.bytedeco.javacv.platform)
                compileOnly(libs.org.bytedeco.ffmpeg.platform.gpl)
            }
        }
    }
}
