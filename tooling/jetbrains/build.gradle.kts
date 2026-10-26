import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.kson.ReleaseArtifactSet

fun properties(key: String) = project.findProperty(key).toString()

plugins {
    kotlin("jvm")
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = properties("pluginGroup")
val isRelease = project.findProperty("release") == "true"
version = org.kson.KsonVersion.getVersion(isRelease = isRelease)

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation(project(":"))
    testImplementation("junit:junit:4.13.2")

    intellijPlatform {
        // IC was merged into a single "IntelliJ IDEA" artifact starting with 2025.3
        val version = properties("platformVersion")
        val major = version.substringBefore(".").toInt()
        val minor = version.substringAfter(".").substringBefore(".").toInt()
        if (major > 2025 || (major == 2025 && minor >= 3)) {
            intellijIdea(version)
        } else {
            intellijIdeaCommunity(version)
        }
        bundledPlugin("org.intellij.intelliLang")

        pluginVerifier()
        zipSigner()
        testFramework(TestFrameworkType.Platform)
    }
}

intellijPlatform {
    projectName = properties("pluginProjectName") // dictates both the directory and the archive name
    pluginConfiguration {
        ideaVersion {
            sinceBuild = properties("pluginSinceBuild")
        }
    }
    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        channels = listOf(if (version.toString().endsWith("-SNAPSHOT")) "SNAPSHOT" else "default")
    }
    pluginVerification {
        ides {
            recommended()
        }
    }
}

java {
    toolchain {
        // The IntelliJ Platform Gradle Plugin defaults the toolchain to the target platform's JDK (17), which our
        // build doesn't provision. Compile with the project JDK instead; `javaVersion` below sets the bytecode level.
        languageVersion = JavaLanguageVersion.current()
    }
}

tasks {
    // Set the JVM compatibility versions
    properties("javaVersion").let {
        withType<JavaCompile> {
            options.release = it.toInt()
        }

        withType<KotlinCompile> {
            compilerOptions {
                jvmTarget.set(JvmTarget.fromTarget(it))
            }
        }
    }
}

tasks.register<Copy>("packageReleasePlugin") {
    group = "distribution"
    description = "Copies the plugin zip for release into the root project's release-artifacts/portable directory"

    from(tasks.buildPlugin)
    into(ReleaseArtifactSet.PORTABLE.stageInto(this))
}
