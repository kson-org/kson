plugins {
    kotlin("multiplatform")
}

repositories {
    mavenCentral()
}

val releaseBuildDir: String = project(":lib-rust").projectDir.resolve("kson-test-server/target/release").absolutePath

val syncCommonTestSources by tasks.registering(Sync::class) {
    from(project(":kson-lib").file("src/commonTest/kotlin"))
    into(layout.buildDirectory.dir("commonTestSources"))
}

val syncJvmTestSources by tasks.registering(Sync::class) {
    from(project(":kson-lib").file("src/jvmTest/kotlin"))
    into(layout.buildDirectory.dir("jvmTestSources"))
}

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
                implementation(project(":kson-lib-http"))
                implementation(kotlin("test"))
            }

            kotlin {
                srcDir(syncCommonTestSources)
            }
        }
        jvmTest {
            dependencies {
                implementation("org.junit.jupiter:junit-jupiter-api:5.14.2")
                runtimeOnly("org.junit.jupiter:junit-jupiter-engine:5.14.2")
            }

            kotlin {
                srcDir(syncJvmTestSources)
            }
        }
    }
}

