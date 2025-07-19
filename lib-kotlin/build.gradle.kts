import org.gradle.kotlin.dsl.support.uppercaseFirstChar
import org.jetbrains.kotlin.konan.target.Architecture
import org.jetbrains.kotlin.konan.target.Family
import org.jetbrains.kotlin.konan.target.HostManager
import org.kson.bindings.BindingsGenerator

plugins {
    kotlin("multiplatform")
    id("com.google.devtools.ksp") version "2.1.21-2.0.2"
}

repositories {
    mavenCentral()
}

val generateBindingsTask = "generateBindings"
group = "org.kson"
version = "1.0-SNAPSHOT"


dependencies {
    add("kspCommonMainMetadata", files("../build/ksp-jars/bindings-ksp-processor.jar"))

    // Needed for the jar from the previous line
    add("kspCommonMainMetadata", "org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
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
        val nativeKsonMain by getting
        val nativeKsonTest by getting
    }
}

tasks {
    val javaVersion = "11"
    withType<JavaCompile> {
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }

    register<GenerateBindingsTask>(generateBindingsTask) {
        dependsOn("kspCommonMainKotlinMetadata", "nativeKsonBinaries")
    }

    BindingsGenerator.languages().forEach {
        val langName = it.targetDir.uppercaseFirstChar()
        val taskName = "test${langName}Bindings"
        register<Exec>(taskName) {
            val commandStr = it.testCommand.joinToString(" ")

            group = "verification"
            description = "Run $commandStr on generated $langName bindings"
            dependsOn(generateBindingsTask)

            val bindingsPath = "build/generated/bindings/${it.targetDir}"
            workingDir = file(bindingsPath)
            commandLine = it.testCommand

            // Let the subprocess find the kson shared library
            val (libraryPathVariable, libraryPathSeparator) = when {
                org.gradle.internal.os.OperatingSystem.current().isWindows -> Pair("PATH", ";")
                else -> Pair("LD_LIBRARY_PATH", ":")
            }
            var libraryPath = System.getenv(libraryPathVariable) ?: ""
            if (libraryPath.isNotEmpty() && !libraryPath.endsWith(libraryPathSeparator)) {
                libraryPath += libraryPathSeparator
            }
            libraryPath += project.file(bindingsPath).absoluteFile.resolve("libkson").toString()
            environment(libraryPathVariable, libraryPath)

            // Show stdout and stderr
            standardOutput = System.out
            errorOutput = System.err

            // Ensure the task fails if the test command fails
            isIgnoreExitValue = false

            doFirst {
                if (!workingDir.exists()) {
                    throw GradleException("$langName bindings directory does not exist: ${workingDir.absolutePath}")
                }
            }
        }

        check {
            dependsOn(taskName)
        }
    }
}
