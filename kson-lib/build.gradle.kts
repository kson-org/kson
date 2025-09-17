import org.jetbrains.kotlin.konan.target.Architecture
import org.jetbrains.kotlin.konan.target.Family
import org.jetbrains.kotlin.konan.target.HostManager

plugins {
    kotlin("multiplatform")
    id("com.vanniktech.maven.publish") version "0.30.0"
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

mavenPublishing {
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL, automaticRelease = false)
    signAllPublications()

    coordinates("org.kson", "kson", version.toString())

    pom {
        name.set("KSON")
        description.set("A 💌 to the humans maintaining computer configurations")
        url.set("https://kson.org")

        licenses {
            license {
                name.set("Apache-2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }

        developers {
            developer {
                id.set("dmarcotte")
                name.set("Daniel Marcotte")
                email.set("kson@kson.org")
            }
        }

        scm {
            connection.set("scm:git:https://github.com/kson-org/kson.git")
            developerConnection.set("scm:git:git@github.com:kson-org/kson.git")
            url.set("https://github.com/kson-org/kson")
        }
    }
}
