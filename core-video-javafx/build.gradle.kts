plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvm {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
                }
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
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.coroutines.javafx)
                implementation(libs.org.bytedeco.javacv.platform)

                val javafxVersion = "17.0.10"
                val modules = listOf("graphics", "controls", "base", "swing")
                val platforms = listOf("win", "mac", "mac-aarch64", "linux")
                
                // 為了解決 "Cannot access 'EventTarget'" 等編譯問題，加入不帶 platform classifier 的 api 依賴
                modules.forEach { module ->
                    api("org.openjfx:javafx-$module:$javafxVersion")
                    platforms.forEach { platform ->
                        implementation("org.openjfx:javafx-$module:$javafxVersion:$platform")
                    }
                }
            }
        }
    }
}
