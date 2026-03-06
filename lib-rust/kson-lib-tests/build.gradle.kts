plugins {
    kotlin("multiplatform")
}

repositories {
    mavenCentral()
}

val releaseBuildDir: String = project(":lib-rust").projectDir.resolve("kson-test-server/target/release").absolutePath

val buildTestServer by tasks.registering(Exec::class) {
    dependsOn(":kson-lib:buildWithGraalVmNativeImage")

    val nativeArtifactsDir = project(":kson-lib").projectDir.resolve("build/kotlin/compileGraalVmNativeImage").absolutePath

    environment(
        Pair("KSON_PREBUILT_BIN_DIR", nativeArtifactsDir),
        Pair("KSON_COPY_SHARED_LIBRARY_TO_DIR", releaseBuildDir),
    )

    val cargoManifestPath = project(":lib-rust").projectDir.resolve("kson-test-server/Cargo.toml")

    group = "build"
    workingDir = project(":lib-rust").projectDir
    commandLine = "./pixiw run cargo build --release --manifest-path $cargoManifestPath".split(" ")
    standardOutput = System.out
    errorOutput = System.err
    isIgnoreExitValue = false
}

tasks.withType<Test> {
    dependsOn(buildTestServer)

    systemProperty("releaseBuildDir", releaseBuildDir)

    useJUnitPlatform()
    jvmArgs("-Djunit.jupiter.extensions.autodetection.enabled=true")
}

kotlin {
    jvm()

    sourceSets {
        commonTest {
            dependencies {
                implementation(project(":kson-service-tests"))
                implementation(project(":kson-http"))
            }
        }
        jvmTest {
            dependencies {
                implementation(kotlin("test-junit5"))

                // Important: this ensures we have a recent-enough version of JUnit, supporting the `AutoCloseable`
                // interface (otherwise test runs never finish because the HTTP server doesn't get closed)
                implementation(project.dependencies.platform("org.junit:junit-bom:5.14.3"))
            }
        }
    }
}

