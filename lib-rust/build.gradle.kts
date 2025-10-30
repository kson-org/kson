plugins{
    base
}

val nativeKsonDir = project.projectDir.parentFile.resolve("kson-lib/build/kotlin/compileGraalVmNativeImage")
val ksonRsTargetDir = project.projectDir.resolve("kson/target/debug")

val testDynamic by tasks.registering(PixiExecTask::class) {
    dependsOn(":kson-lib:buildWithGraalVmNativeImage")

    group="verification"
    command=listOf("cargo", "test", "--manifest-path", "kson/Cargo.toml")
    envVars=mapOf(
        Pair("KSON_PREBUILT_BIN_DIR", nativeKsonDir.absolutePath),
        Pair("KSON_COPY_SHARED_LIBRARY_TO_DIR", ksonRsTargetDir.absolutePath),
    )
}

tasks{
    check {
        dependsOn(testDynamic)
    }
}