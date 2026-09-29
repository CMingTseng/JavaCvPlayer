plugins {
    kotlin("multiplatform")
}

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
                implementation(libs.skiko)
                implementation(libs.kermit)

                compileOnly(libs.org.bytedeco.ffmpeg)
                compileOnly(libs.org.bytedeco.javacv.platform)
            }
        }
    }
}
