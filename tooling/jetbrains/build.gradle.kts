import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

fun properties(key: String) = project.findProperty(key).toString()

plugins {
    kotlin("jvm")
    id("org.jetbrains.intellij.platform") version "2.13.1"
}

group = properties("pluginGroup")
// [[kson-version-num]] - base version defined in buildSrc/src/main/kotlin/org/kson/KsonVersion.kt
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
    pluginConfiguration {
        name = properties("pluginName")
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
}
