import gobley.gradle.GobleyHost
import gobley.gradle.Variant
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
    alias(libs.plugins.vanniktechPublish)
    alias(libs.plugins.kotlinAtomicfu)
}

cargo {
    packageDirectory = layout.projectDirectory.dir("src/commonMain/rust")
    nativeVariant = Variant.Release
    jvmPublishingVariant = Variant.Release
    // Bundle native libs into the main jvmJar (fat JAR) instead of separate classifier JARs,
    publishJvmArtifacts = false
    builds.jvm {
        if (System.getenv("CI") == null) {
            embedRustLibrary = (rustTarget == GobleyHost.current.rustTarget)
        }
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
    val osName = System.getProperty("os.name").lowercase()
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
        publishLibraryVariants("release")
    }

    if (osName.contains("mac")) {
        listOf(
            iosArm64(),
            iosSimulatorArm64()
        ).forEach { iosTarget ->
            iosTarget.binaries.framework {
                baseName = "CoilResvg"
                isStatic = true
            }
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
    ndkVersion = "28.0.13004108"

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
        ndk.abiFilters += setOf("arm64-v8a", "x86_64")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

mavenPublishing {
    publishToMavenCentral()

    if (System.getenv("CI") != null) {
        signAllPublications()
    }

    val tag = System.getenv("GITHUB_REF")
        ?.substringAfterLast("/")
        ?.removePrefix("v")
        ?: property("lib.version") as String

    coordinates(
        groupId = "com.hashsequence",
        artifactId = "coil-resvg",
        version = tag
    )

    pom {
        name = "Coil Resvg Decoder"
        description = "A Kotlin Multiplatform SVG decoder for Coil, powered by resvg (Rust)."
        url = "https://github.com/hash-sequence/coil-resvg"

        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
            }
        }

        developers {
            developer {
                id = "hash-sequence"
                name = "HashSequence"
            }
        }

        scm {
            connection = "scm:git:git://github.com/hash-sequence/coil-resvg.git"
            developerConnection = "scm:git:ssh://github.com/hash-sequence/coil-resvg.git"
            url = "https://github.com/hash-sequence/coil-resvg"
        }
    }
}

// Merge Rust native libraries into the main jvmJar so consumers get them automatically.
// Local dev: only current host platform; CI: all platforms (fat JAR).
gradle.projectsEvaluated {
    val proj = project(":coil-resvg")
    val jvmJarTask = proj.tasks.named<Jar>("jvmJar")

    val isCI = System.getenv("CI") != null
    val hostPlatformSuffix = when {
        GobleyHost.current.rustTarget.toString().contains("aarch64-apple") -> "MacOSArm64"
        GobleyHost.current.rustTarget.toString().contains("x86_64-apple") -> "MacOSX64"
        GobleyHost.current.rustTarget.toString().contains("x86_64-unknown-linux") -> "LinuxX64"
        GobleyHost.current.rustTarget.toString().contains("aarch64-unknown-linux") -> "LinuxArm64"
        GobleyHost.current.rustTarget.toString().contains("x86_64-pc-windows") -> "MinGWX64"
        else -> null
    }

    proj.tasks.names
        .filter { taskName ->
            taskName.startsWith("jarJvmRustRuntime")
                    && taskName.endsWith("Release")
                    && (isCI || hostPlatformSuffix == null || taskName.contains(hostPlatformSuffix))
        }
        .forEach { taskName ->
            val nativeJarProvider = proj.tasks.named(taskName)
            jvmJarTask.configure {
                dependsOn(nativeJarProvider)
                from(nativeJarProvider.map { jarTask ->
                    zipTree((jarTask as org.gradle.jvm.tasks.Jar).archiveFile)
                }) {
                    exclude("**/META-INF/**")
                }
            }
        }
}
