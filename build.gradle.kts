import org.gradle.api.tasks.testing.logging.TestLogEvent.*
import org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest
import org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest
import java.util.*

val sharedProps = Properties().apply {
    project.file("jdk.properties").inputStream().use { load(it) }
}

plugins {
    kotlin("multiplatform") version "1.9.22"

    // configured by `jvmWrapper` block below
    id("me.filippov.gradle.jvm.wrapper") version "0.14.0"
}

// NOTE: `./gradlew wrapper` must be run for edit to this config to take effect
jvmWrapper {
    unixJvmInstallDir = sharedProps.getProperty("unixJvmInstallDir")
    winJvmInstallDir = sharedProps.getProperty("winJvmInstallDir")
    macAarch64JvmUrl = sharedProps.getProperty("macAarch64JvmUrl")
    macX64JvmUrl = sharedProps.getProperty("macX64JvmUrl")
    linuxAarch64JvmUrl = sharedProps.getProperty("linuxAarch64JvmUrl")
    linuxX64JvmUrl = sharedProps.getProperty("linuxX64JvmUrl")
    windowsX64JvmUrl = sharedProps.getProperty("windowsX64JvmUrl")
}

group = "org.kson"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val generateJsonTestSuiteTask = "generateJsonTestSuite"

tasks {
    register<GenerateJsonTestSuiteTask>(generateJsonTestSuiteTask)

    withType<Task> {
        // make every task except itself depend on generateJsonTestSuiteTask to
        // ensure it's always up-to-date before any other build steps
        if (name != generateJsonTestSuiteTask) {
            dependsOn(generateJsonTestSuiteTask)
        }
    }

    withType<KotlinJvmTest> {
        testLogging.showStandardStreams = true
        testLogging.events = setOf(PASSED, SKIPPED, FAILED, STANDARD_OUT, STANDARD_ERROR)
    }

    withType<KotlinJsTest> {
        testLogging.showStandardStreams = true
        testLogging.events = setOf(PASSED, SKIPPED, FAILED, STANDARD_OUT, STANDARD_ERROR)
    }

    withType<KotlinNativeTest> {
        testLogging.showStandardStreams = true
        testLogging.events = setOf(PASSED, SKIPPED, FAILED, STANDARD_OUT, STANDARD_ERROR)
    }

    /**
     * Work around Gradle complaining about duplicate readmes in the mpp build.  Related context:
     * - https://github.com/gradle/gradle/issues/17236
     * - https://youtrack.jetbrains.com/issue/KT-46978
     */

    @Suppress("UnstableApiUsage")
    withType<ProcessResources> {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
}

kotlin {
    jvm {
        testRuns["test"].executionTask.configure {
            useJUnit()
        }
    }
    js(IR) {
        browser {
            testTask {
                useKarma {
                    useChromeHeadless()
                }
            }
        }
    }
    val hostOs = System.getProperty("os.name")
    val isMingwX64 = hostOs.startsWith("Windows")
    when {
        hostOs == "Mac OS X" -> macosX64("nativeKson")
        hostOs == "Linux" -> linuxX64("nativeKson")
        isMingwX64 -> mingwX64("nativeKson")
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }

    sourceSets {
        val commonMain by getting
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }
        val jvmMain by getting
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
            }
        }
        val jsMain by getting
        val jsTest by getting {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }
        val nativeKsonMain by getting
        val nativeKsonTest by getting
    }
}

/**
 * The default node version being used by Kotlin (14.17.0) is not compatible with Apple silicon,
 * so we manually set our node version to the recent Apple silicon-compatible LTS release as described here:
 * https://youtrack.jetbrains.com/issue/KT-49109#focus=Comments-27-5259190.0-0
 */
rootProject.plugins.withType<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin> {
    rootProject.the<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension>().apply {
        nodeVersion = "16.14.2"
    }
}