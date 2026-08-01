plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
}

kotlin {
    androidTarget()
    // 日常 Android 构建不初始化 Apple 工具链；有 Xcode 后用 -PenableIos=true 开启。
    if (providers.gradleProperty("enableIos").orNull == "true") {
        listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
            target.binaries.framework {
                baseName = "CashFlowShared"
                isStatic = true
            }
        }
    }

    jvmToolchain(17)

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

android {
    namespace = "com.qiyue.cashflow.shared"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
    }
}
