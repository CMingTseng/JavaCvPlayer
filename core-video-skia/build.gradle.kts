plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.compose.compiler)
    `maven-publish`
}

group = "idv.neo.ffmpeg.media.player"
version = "1.0-SNAPSHOT"

kotlin {
    jvm()

    sourceSets {
        val jvmMain by getting {
            dependencies {
                compileOnly(libs.kotlin.stdlib)
                compileOnly(libs.kotlinx.coroutines.core)
                implementation(project(":core"))
//                implementation(project(":lib-common-lite"))
                implementation("com.github.cybernhl.media:lib-common-lite:727538c430")
                implementation(compose.desktop.currentOs)
                implementation(libs.kermit)

                compileOnly("org.bytedeco:ffmpeg:8.1.2-1.5.14")
                compileOnly(libs.org.bytedeco.javacv.platform)
            }
        }
    }
}
