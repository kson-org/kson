import org.gradle.api.tasks.testing.logging.TestLogEvent.*
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.*

// the path on disk of the main project which this buildSrc project supports
val mainProjectRoot = rootDir.parent!!

val jdkPropertiesFile = project.file("../jdk.properties")
val jdkProperties = Properties().apply {
    jdkPropertiesFile.inputStream().use { load(it) }
}

val jdkVersion = jdkProperties.getProperty("JDK_VERSION")!!

plugins {
    kotlin("jvm") version "2.2.20"
    kotlin("plugin.serialization") version "2.2.20"

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
    configureEach {
        // unless we are running a wrapper task which may be trying change our JDK,
        // ensure we're running the expected version
        if (name != "wrapper" && gradle.parent?.startParameter?.taskNames != listOf("wrapper")) {
            doFirst {
                if (jdkVersion != System.getProperty("java.version")) {
                    throw RuntimeException("ERROR: incorrect JVM version detected: ${System.getProperty("java.version")}. Expected: $jdkVersion.  " +
                            "Troubleshooting suggestions:\n" +
                            "  - Did `${jdkPropertiesFile.absolutePath}` change?\n    If yes, you likely need to invoke `./gradlew wrapper`\n" +
                            "  - If this error is in your IDE, consult the \"Development setup / IntelliJ setup\"\n" +
                            "    section of readme.md")
                } else {
                    println("Project JDK: v$jdkVersion loaded from ${System.getProperty("java.home")}")
                }
            }
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

        /**
         * The `ProjectBuilder.builder().build()` used in [GenerateJsonTestSuiteTaskTest.sanityCheckTask]
         * triggered this issue: https://github.com/gradle/gradle/issues/18647.  This is the workaround
         * described in the thread there: https://github.com/gradle/gradle/issues/18647#issuecomment-1189222029
         */
        jvmArgs = listOf(
            "--add-opens", "java.base/java.lang=ALL-UNNAMED",
        )
    }
}

dependencies {
    implementation(gradleApi())
    testImplementation(kotlin("test"))
    implementation("org.eclipse.jgit:org.eclipse.jgit:6.7.0.202309050840-r")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.1")
    
    // Add Kotlin Gradle Plugin for multiplatform configuration
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.20")

    implementation("org.kson:kson:0.1.1")
}
