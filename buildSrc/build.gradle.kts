import org.gradle.api.tasks.testing.logging.TestLogEvent.*
import java.util.*

// the path on disk of the main project which this buildSrc project supports
val mainProjectRoot = rootDir.parent!!

val jdkProperties = Properties().apply {
    project.file("../jdk.properties").inputStream().use { load(it) }
}

val jdkVersion = jdkProperties.getProperty("JDK_VERSION")!!
val badJdkVersionError = jdkProperties.getProperty("BAD_JDK_VERSION_ERROR")!!
if (jdkVersion != System.getProperty("java.version")) {
    throw RuntimeException("$badJdkVersionError: ${System.getProperty("java.version")}. Expected: $jdkVersion")
} else {
    println("Project JDK: v$jdkVersion loaded from ${System.getProperty("java.home")}")
}

plugins {
    kotlin("jvm") version "1.9.22"
    kotlin("plugin.serialization") version "1.9.22"

    // configured by `jvmWrapper` block below
    id("me.filippov.gradle.jvm.wrapper") version "0.14.0"
}

// NOTE: `./gradlew wrapper` must be run for edits to this config to take effect
jvmWrapper {
    unixJvmInstallDir = jdkProperties.getProperty("unixJvmInstallDir")
    winJvmInstallDir = jdkProperties.getProperty("winJvmInstallDir")
    macAarch64JvmUrl = jdkProperties.getProperty("macAarch64JvmUrl")
    macX64JvmUrl = jdkProperties.getProperty("macX64JvmUrl")
    linuxAarch64JvmUrl = jdkProperties.getProperty("linuxAarch64JvmUrl")
    linuxX64JvmUrl = jdkProperties.getProperty("linuxX64JvmUrl")
    windowsX64JvmUrl = jdkProperties.getProperty("windowsX64JvmUrl")
}

repositories {
    mavenCentral()
}

tasks {
    named<Wrapper>("wrapper") {
        // always run when invoked
        outputs.upToDateWhen { false }

        doFirst {
            val wrapperProperties = Properties().apply {
                project.file("../gradle/wrapper/gradle-wrapper.properties").inputStream().use { load(it) }
            }

            // ensure our Gradle version/distro is kept in sync with our parent project
            distributionUrl = wrapperProperties.getProperty("distributionUrl")
        }

        doLast {
            println(":buildSrc:wrapper -> Generated buildSrc/ wrapper using root project distributionUrl")
        }
    }

    withType<Test> {
        testLogging.showStandardStreams = true
        testLogging.events = setOf(PASSED, SKIPPED, FAILED, STANDARD_OUT, STANDARD_ERROR)
    }
}

dependencies {
    implementation(gradleApi())
    testImplementation(kotlin("test"))
    implementation("org.eclipse.jgit:org.eclipse.jgit:6.7.0.202309050840-r")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.1")
}