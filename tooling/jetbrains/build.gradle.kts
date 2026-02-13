import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

fun properties(key: String) = project.findProperty(key).toString()

plugins {
    kotlin("jvm")
    // Gradle IntelliJ Plugin
    id("org.jetbrains.intellij") version "1.17.2"
}

group = properties("pluginGroup")
// [[kson-version-num]] - base version defined in buildSrc/src/main/kotlin/org/kson/KsonVersion.kt
val isRelease = project.findProperty("release") == "true"
version = org.kson.KsonVersion.getVersion(rootProject.projectDir, isRelease = isRelease)

// Configure project's dependencies
repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":"))
}

// Configure Gradle IntelliJ Plugin - read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {
    pluginName.set(properties("pluginName"))
    version.set(properties("platformVersion"))
    type.set(properties("platformType"))

    // Plugin Dependencies. Uses `platformPlugins` property from the gradle.properties file.
    plugins.set(properties("platformPlugins").split(',').map(String::trim).filter(String::isNotEmpty))
}

tasks {
    // Set the JVM compatibility versions
    properties("javaVersion").let {
        withType<JavaCompile> {
            sourceCompatibility = it
            targetCompatibility = it
        }

        withType<KotlinCompile> {
            compilerOptions {
                jvmTarget.set(JvmTarget.fromTarget(it))
            }
        }
    }

    // Configure UI tests plugin
    // Read more: https://github.com/JetBrains/intellij-ui-test-robot
    runIdeForUiTests {
        systemProperty("robot-server.port", "8082")
        systemProperty("ide.mac.message.dialogs.as.sheets", "false")
        systemProperty("jb.privacy.policy.text", "<!--999.999-->")
        systemProperty("jb.consents.confirmation.enabled", "false")
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
        // Use SNAPSHOT channel for snapshot builds, default channel for releases
        channels.set(listOf(if (version.toString().endsWith("-SNAPSHOT")) "SNAPSHOT" else "default"))
    }

    patchPluginXml {
        sinceBuild.set(properties("pluginSinceBuild"))
    }

    /**
     * Work around a "class is a duplicate but no duplicate handling strategy has been set" error that
     * started popping up when we upgraded to Kotlin 1.8
     */
    jar {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    verifyPlugin {
        ignoreWarnings.set(false)
        ignoreUnacceptableWarnings.set(false)
    }
}