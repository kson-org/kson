import org.gradle.api.tasks.testing.logging.TestLogEvent.*
import org.gradle.tooling.GradleConnector
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest
import org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.kson.KsonVersion
import java.util.*

val sharedProps = Properties().apply {
    project.file("jdk.properties").inputStream().use { load(it) }
}

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.vanniktech.maven.publish") version "0.30.0"
    id("org.jetbrains.dokka") version "2.0.0"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"

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

    val transpileDetektConfigTask by register<TranspileKsonToYaml>("transpileDetektConfigTask") {
        ksonFile.set(project.file("detekt.kson"))
        yamlFile.set(project.file("detekt.yml"))
    }

    register<VerifyCleanCheckoutTask>("verifyCleanCheckout")

    withType<Task> {
        // make every task except itself depend on generateJsonTestSuiteTask, transpileCircleCIConfigTask,
        // and transpileDetektConfigTask to ensure they're always up-to-date before any other build steps
        if (name != generateJsonTestSuiteTask.name
            && name != transpileCircleCiConfigTask.name
            && name != transpileDetektConfigTask.name) {
            dependsOn(generateJsonTestSuiteTask, transpileCircleCiConfigTask, transpileDetektConfigTask)
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
val internalBaseVersion = "x.4"
val isRelease = project.findProperty("release") == "true"
version = KsonVersion.getVersion(internalBaseVersion, isRelease)

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
    }
}

mavenPublishing {
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL, automaticRelease = false)
    signAllPublications()

    coordinates("org.kson", "kson-internals", KsonVersion.getPublishVersion(projectDir, internalBaseVersion, isRelease))

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

allprojects {
    plugins.withId("org.jetbrains.kotlin.multiplatform") { applyDetekt(project) }
    plugins.withId("org.jetbrains.kotlin.jvm") { applyDetekt(project) }
}

fun applyDetekt(project: Project) = with(project) {
    apply(plugin = "io.gitlab.arturbosch.detekt")
    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        buildUponDefaultConfig = true
        config.setFrom(files("$rootDir/detekt.yml"))
        basePath = rootDir.absolutePath
        source.setFrom(files("src"))
        // Known structural findings on the existing codebase are grandfathered per-module.
        // New code is still scrutinized against the rules.
        baseline = file("detekt-baseline.xml")
    }
    val transpileConfig = rootProject.tasks.named("transpileDetektConfigTask")
    tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
        dependsOn(transpileConfig)
        reports {
            html.required.set(true)
            xml.required.set(false)
            txt.required.set(true)
            sarif.required.set(false)
            md.required.set(false)
        }
    }
    tasks.withType<io.gitlab.arturbosch.detekt.DetektCreateBaselineTask>().configureEach {
        dependsOn(transpileConfig)
    }
}
