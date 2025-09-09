plugins{
    base
}

val testStatic by tasks.registering(PixiExecTask::class){
    group="verification"
    command=listOf("cargo", "test", "--manifest-path", "kson/Cargo.toml")
}

val testDynamic by tasks.registering(PixiExecTask::class){
    group="verification"
    command=listOf("cargo", "test", "--manifest-path", "kson/Cargo.toml", "--features", "dynamic-linking")
}

val formattingCheck by tasks.registering(PixiExecTask::class){
    group="verification"
    command=listOf("cargo", "fmt", "--manifest-path", "kson/Cargo.toml", "--check")
}

val formattingCheckSys by tasks.registering(PixiExecTask::class){
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