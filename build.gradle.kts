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
    id("com.vanniktech.maven.publish") version "0.30.0"
    id("org.jetbrains.dokka") version "2.0.0"

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

tasks {
    val generateJsonTestSuiteTask by register<GenerateJsonTestSuiteTask>("generateJsonTestSuite")

    val transpileCircleCiConfigTask by register<TranspileKsonToYaml>("transpileCircleCiConfigTask") {
        ksonFile.set(project.file(".circleci/config.kson"))
        yamlFile.set(project.file(".circleci/config.yml"))
    }

    withType<Task> {
        // make every task except itself depend on generateJsonTestSuiteTask and transpileCircleCIConfigTask to
        // ensure it's always up-to-date before any other build steps
        if (name != generateJsonTestSuiteTask.name && name != transpileCircleCiConfigTask.name ) {
            dependsOn(generateJsonTestSuiteTask, transpileCircleCiConfigTask)
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

group = "org.kson"
/**
 * We use x.[incrementing number] version here since this in not intended for general consumption.
 *   This version number is both easy to increment and (hopefully) telegraphs well with the strange
 *   versioning that this should not be depended on
 * [[kson-version-num]]
 */
version = "x.2"

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

mavenPublishing {
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL, automaticRelease = false)
    signAllPublications()

    coordinates("org.kson", "kson-internals", version.toString())

    pom {
        name.set("KSON Internals")
        description.set("Internal implementation details of KSON. This package is not intended for direct use. Please use the 'org.kson:kson' package instead for the stable public API.")
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
                email.set("daniel@kson.org")
            }
        }

        scm {
            connection.set("scm:git:https://github.com/kson-org/kson.git")
            developerConnection.set("scm:git:git@github.com:kson-org/kson.git")
            url.set("https://github.com/kson-org/kson")
        }
    }
}
