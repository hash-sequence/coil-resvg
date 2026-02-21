import gobley.gradle.GobleyHost
import gobley.gradle.cargo.dsl.appleMobile
import gobley.gradle.cargo.dsl.jvm
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.gobleyCargo)
    alias(libs.plugins.devGobleyUniffi)
    kotlin("plugin.atomicfu") version libs.versions.kotlin
}

cargo {
    packageDirectory = layout.projectDirectory.dir("src/commonMain/rust")
    builds.jvm {
        embedRustLibrary = (rustTarget == GobleyHost.current.rustTarget)
    }
    builds.appleMobile {
        variants {
            buildTaskProvider.configure {
                when (rustTarget.cinteropName) {
                    "ios" -> additionalEnvironment.put("IPHONEOS_DEPLOYMENT_TARGET", "15.0.0")
                    "tvos" -> additionalEnvironment.put("TVOS_DEPLOYMENT_TARGET", "15.0.0")
                    "watchos" -> additionalEnvironment.put("WATCHOS_DEPLOYMENT_TARGET", "9.0.0")
                    else -> {}
                }
            }
        }
    }
}

uniffi {
    generateFromLibrary {
        packageName = "com.hashsequence.coilresvg"
    }
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "CoilResvg"
            isStatic = true
        }
    }

    jvm()

    js {
        browser()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
        }
        commonMain.dependencies {
            implementation(libs.coil.compose)
        }
    }
}

android {
    namespace = "com.hashsequence.coilresvg"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
        ndk.abiFilters += setOf("arm64-v8a", "x86_64")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
