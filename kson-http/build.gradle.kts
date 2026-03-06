plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

repositories {
    mavenCentral()
}

val ktorVersion = "3.4.0"

kotlin {
    jvm {
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":kson-service-api"))

                implementation("io.ktor:ktor-client-core:$ktorVersion")
                implementation("io.ktor:ktor-client-cio:$ktorVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
            }
        }
    }
}

