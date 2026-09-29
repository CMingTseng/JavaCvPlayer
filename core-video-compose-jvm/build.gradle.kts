import shadow.bundletool.com.android.tools.r8.diagnostic.internal.c

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
                implementation(project(":core"))
//                implementation(project(":lib-common-lite"))
                implementation("com.github.cybernhl.media:lib-common-lite:727538c430")
                implementation(libs.kermit)
                compileOnly(libs.org.bytedeco.javacv.platform)
            }
        }
    }
}
