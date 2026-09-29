plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvm {
        @OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
        compilations.all {
            compilerOptions.configure {
                jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
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
