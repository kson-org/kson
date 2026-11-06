import org.gradle.internal.os.OperatingSystem

plugins{
    base
}

val nativeKsonDir = project.projectDir.parentFile.resolve("kson-lib/build/kotlin/compileGraalVmNativeImage")
val staticKsonDir = project.projectDir.parentFile.resolve("kson-lib/build/kotlin/graalVmStaticLibrary")
val ksonRsTargetDir = project.projectDir.resolve("kson/target")

val testDynamic by tasks.registering(PixiExecTask::class) {
    dependsOn(":kson-lib:buildWithGraalVmNativeImage")

    group="verification"
    command=listOf("cargo", "test", "--manifest-path", "kson/Cargo.toml")
    envVars=mapOf(
        Pair("KSON_PREBUILT_BIN_DIR", nativeKsonDir.absolutePath),
        Pair("KSON_COPY_SHARED_LIBRARY_TO_DIR", ksonRsTargetDir.resolve("debug").absolutePath),
    )
}

val testStatic by tasks.registering(PixiExecTask::class) {
    dependsOn(":kson-lib:buildGraalVmStaticLibrary")

    group="verification"
    command=listOf("cargo", "test", "--manifest-path", "kson/Cargo.toml", "--features", "static")
    envVars=mapOf(
        Pair("KSON_PREBUILT_BIN_DIR", staticKsonDir.absolutePath),
    )
}

tasks{
    check {
        dependsOn(testDynamic)
        // the static library is macOS and Linux only
        if (!OperatingSystem.current().isWindows) {
            dependsOn(testStatic)
        }
    }

    clean {
        delete(ksonRsTargetDir)
    }
}