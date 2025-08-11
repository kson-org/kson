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

val copyNativeArtifactsToBindingsTask = "copyNativeArtifactsToBindings"

tasks {
    register<CopyNativeArtifactsToBindingsTask>(copyNativeArtifactsToBindingsTask) {
        dependsOn("nativeKsonBinaries")
    }

    val projectRoot = project.projectDir.parentFile.toPath()
    KsonBindings.ALL.forEach {
        val taskName = "test-${it.dir}"
        register<Exec>(taskName) {
            dependsOn(copyNativeArtifactsToBindingsTask)

            val commandStr = it.testCommand
            val bindingsDir = projectRoot.resolve(it.dir)

            group = "verification"
            description = "Run $commandStr on generated ${it.dir}"
            workingDir = file(bindingsDir)
            commandLine = it.testCommand.split(" ")

            // Let the subprocess find the kson shared library
            val (libraryPathVariable, libraryPathSeparator) = when {
                org.gradle.internal.os.OperatingSystem.current().isWindows -> Pair("PATH", ";")
                else -> Pair("LD_LIBRARY_PATH", ":")
            }
            var libraryPath = System.getenv(libraryPathVariable) ?: ""
            if (libraryPath.isNotEmpty() && !libraryPath.endsWith(libraryPathSeparator)) {
                libraryPath += libraryPathSeparator
            }
            libraryPath += project.file(bindingsDir)
            environment(libraryPathVariable, libraryPath)

            // Show stdout and stderr
            standardOutput = System.out
            errorOutput = System.err

            // Ensure the task fails if the test command fails
            isIgnoreExitValue = false

            doFirst {
                if (!workingDir.exists()) {
                    throw GradleException("$bindingsDir directory does not exist: ${workingDir.absolutePath}")
                }
            }
        }

        check {
            dependsOn(taskName)
        }
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
