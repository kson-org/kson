import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    id("org.graalvm.buildtools.native") version "0.11.1"
}

repositories {
    mavenCentral()
}

dependencies {
    // Depend on the core JVM artifact, which GraalVM Native Image will compile
    // to native (along with the tests).
    implementation(project(":"))

    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")
    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named("check") {
    dependsOn("nativeTest")
}

// Allow access to `internal` declarations from kson core
tasks.named<KotlinCompile>("compileTestKotlin") {
    val rootJar = project(":").tasks.named("jvmJar", Jar::class.java).flatMap { it.archiveFile }
    compilerOptions {
        freeCompilerArgs.add(rootJar.map { "-Xfriend-paths=${it.asFile.absolutePath}" })
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

graalvmNative {
    binaries {
        testSupport.set(true)
    }
}

sourceSets {
    test {
        kotlin {
            srcDir(project(":").file("src/commonTest/kotlin"))

            // Exclude expect/actual YamlValidator, because that syntax is only supported in
            // multiplatform Kotlin (and this is a Kotlin/JVM project). We use a no-op
            // implementation instead.
            exclude("**/testSupport/YamlValidator.kt")
        }
    }
}
