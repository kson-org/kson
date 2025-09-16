import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform

plugins{
    base
}

val nativeKsonDir = project.projectDir.parentFile.resolve("kson-lib/build/bin/nativeKson")

val testStatic by tasks.registering(PixiExecTask::class) {
    dependsOn(":kson-lib:nativeRelease")
    onlyIf { !DefaultNativePlatform.getCurrentOperatingSystem().isWindows }

    group="verification"
    command=listOf("cargo", "test", "--manifest-path", "kson/Cargo.toml")
    envVars=mapOf(Pair("KSON_PREBUILT_BIN_DIR", nativeKsonDir.resolve("releaseStatic").absolutePath))
}

val testDynamic by tasks.registering(PixiExecTask::class) {
    dependsOn(":kson-lib:nativeRelease")

    group="verification"
    command=listOf("cargo", "test", "--manifest-path", "kson/Cargo.toml", "--features", "dynamic-linking")
    envVars=mapOf(Pair("KSON_PREBUILT_BIN_DIR", nativeKsonDir.resolve("releaseShared").absolutePath))
}

val formattingCheck by tasks.registering(PixiExecTask::class) {
    group="verification"
    command=listOf("cargo", "fmt", "--manifest-path", "kson/Cargo.toml", "--check")
}

val formattingCheckSys by tasks.registering(PixiExecTask::class) {
    group="verification"
    command=listOf("cargo", "fmt", "--manifest-path", "kson-sys/Cargo.toml", "--check")
}

tasks{
    check {
        dependsOn(testStatic)
        dependsOn(testDynamic)
        dependsOn(formattingCheck)
    }
}