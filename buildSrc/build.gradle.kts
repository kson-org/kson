import org.gradle.api.tasks.testing.logging.TestLogEvent.*
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.7.21"
}

repositories {
    mavenCentral()
}

tasks {
    withType<Test> {
        testLogging.showStandardStreams = true
        testLogging.events = setOf(PASSED, SKIPPED, FAILED, STANDARD_OUT, STANDARD_ERROR)
    }

    withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = "11"
        }
    }
}

dependencies {
    implementation(gradleApi())
    testImplementation(kotlin("test"))
    implementation("org.eclipse.jgit:org.eclipse.jgit:6.7.0.202309050840-r")
}