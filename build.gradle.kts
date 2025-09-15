import org.gradle.api.tasks.testing.logging.TestLogEvent.*
import org.gradle.tooling.GradleConnector
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest
import org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.konan.target.Architecture
import org.jetbrains.kotlin.konan.target.Family
import org.jetbrains.kotlin.konan.target.HostManager
import java.util.*

val sharedProps = Properties().apply {
    project.file("jdk.properties").inputStream().use { load(it) }
}

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")

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

    val javaVersion = "11"
    withType<JavaCompile> {
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }

    withType<KotlinCompile> {
        compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget(javaVersion))
        }
    }

    named<Wrapper>("wrapper") {
        // always run when invoked
        outputs.upToDateWhen { false }

        // ensure DistributionType.ALL so we pull in the source code
        distributionType = Wrapper.DistributionType.ALL

        // ensure buildSrc/ regenerates its wrapper whenever we do
        doLast {
            project.file("buildSrc").let { buildSrcDir ->
                GradleConnector.newConnector().apply {
                    useInstallation(gradle.gradleHomeDir)
                    forProjectDirectory(buildSrcDir)
                }.connect().use { connection ->
                    connection.newBuild()
                        .forTasks("wrapper")
                        .setStandardOutput(System.out)
                        .setStandardError(System.err)
                        .run()
                }
            }
            println("Generated Gradle wrapper for both root and buildSrc")
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
    withType<ProcessResources> {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
}

kotlin {
    jvm()
    js(IR) {
        browser {
            testTask {
                useKarma {
                    useChromeHeadless()
                }
            }
        }
        nodejs {
            testTask {
                useMocha()
            }
        }
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
        val commonMain by getting
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")
            }
        }
        val jvmMain by getting
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
                implementation("org.yaml:snakeyaml:2.2")
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
