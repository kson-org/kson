import org.jetbrains.kotlin.konan.target.Architecture
import org.jetbrains.kotlin.konan.target.Family
import org.jetbrains.kotlin.konan.target.HostManager

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.dokka") version "2.0.0"
    `maven-publish`
}

repositories {
    mavenCentral()
}

group = "org.kson"
version = "0.1.0-SNAPSHOT"

tasks {
    val copyHeaderDynamic = register<CopyNativeHeaderTask>("copyNativeHeaderDynamic") {
        dependsOn(":kson-lib:nativeKsonBinaries")
        useDynamicLinking = true
        outputDir = project.projectDir.resolve("build/nativeHeaders")
    }

    val copyHeaderStatic = register<CopyNativeHeaderTask>("copyNativeHeaderStatic") {
        dependsOn(":kson-lib:nativeKsonBinaries")
        useDynamicLinking = false
        outputDir = project.projectDir.resolve("build/nativeHeaders")
    }

    register<Task>("nativeRelease") {
        dependsOn(":kson-lib:nativeKsonBinaries", copyHeaderDynamic, copyHeaderStatic)
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

publishing {
    publications {
        withType<MavenPublication> {
            artifactId = when (name) {
                "kotlinMultiplatform" -> "kson"
                "jvm" -> "kson-jvm"
                "js" -> "kson-js"
                "nativeKson" -> "kson-${HostManager.host.family.name.lowercase()}-${HostManager.host.architecture.name.lowercase()}"
                else -> throw RuntimeException("Unexpected artifact name: $name. Do we need to add a case here?")
            }
            pom {
                name.set("KSON")
                url.set("https://kson.org")
            }
        }
    }
    repositories {
        mavenLocal()
    }
}
