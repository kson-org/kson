import org.jetbrains.kotlin.konan.target.Architecture
import org.jetbrains.kotlin.konan.target.Family
import org.jetbrains.kotlin.konan.target.HostManager

plugins {
    kotlin("multiplatform")
}

repositories {
    mavenCentral()
}

group = "org.kson"
version = "1.0-SNAPSHOT"

tasks {
    register<CopyNativeHeaderTask>("copyNativeHeaderDynamic") {
        dependsOn(":lib-kotlin:nativeKsonBinaries")
        useDynamicLinking = true
        outputDir = project.projectDir.resolve("build/nativeHeaders")
    }

    register<CopyNativeHeaderTask>("copyNativeHeaderStatic") {
        dependsOn(":lib-kotlin:nativeKsonBinaries")
        useDynamicLinking = false
        outputDir = project.projectDir.resolve("build/nativeHeaders")
    }
}

kotlin {
    jvm {
        testRuns["test"].executionTask.configure {
            useJUnit()
        }
    }
    js(IR) {
        browser()
        nodejs()
        binaries.library()
        useEsModules()
        generateTypeScriptDefinitions()
    }

    val host = HostManager.host
    val nativeTarget = when (host.family) {
        Family.OSX -> when (host.architecture) {
            Architecture.ARM64 -> macosArm64("nativeKson")
            else -> macosX64("nativeKson")
        }
        Family.LINUX -> linuxX64("nativeKson")
        Family.MINGW -> mingwX64("nativeKson")
        Family.IOS, Family.TVOS, Family.WATCHOS, Family.ANDROID -> {
            throw GradleException("Host OS '${host.name}' is not supported in Kotlin/Native.")
        }
    }

    nativeTarget.apply {
        binaries {
            sharedLib {
                baseName = "kson"
            }

            staticLib {
                baseName = "kson"
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
