plugins {
    kotlin("jvm")
    kotlin("plugin.serialization") version "2.1.21"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.devtools.ksp:symbol-processing-api:2.1.21-2.0.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
}
