import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization") version "2.1.10"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(gradleApi())
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")

    // Necessary to obtain metadata about Kson's public API through a KSP processor
    implementation("com.google.devtools.ksp:symbol-processing-api:2.1.21-2.0.2")
}

tasks {
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
}

// The KSP processor gets compiled as a JAR because the root build cannot directly depend on
// buildSrc projects
tasks.jar {
    archiveFileName.set("bindings-ksp-processor.jar")
    destinationDirectory.set(file("../../build/ksp-jars"))
}

tasks.build {
    dependsOn(tasks.jar)
}
