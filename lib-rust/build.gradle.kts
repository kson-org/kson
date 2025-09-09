import org.gradle.internal.os.OperatingSystem

val formattingCheck = "formattingCheckKson"
val formattingCheckSys = "formattingCheckKsonSys"
val testStatic = "testStaticallyLinked"
val testDynamic = "testDynamicallyLinked"

plugins{
    base
}

tasks {
    pixiExec(testStatic, "cargo", "test", "--manifest-path", "kson/Cargo.toml") {
        group = "verification"
        onlyIf { !OperatingSystem.current().isWindows }
    }

    pixiExec(testDynamic, "cargo", "test", "--manifest-path", "kson/Cargo.toml", "--features", "dynamic-linking") {
        group = "verification"
    }

    pixiExec(formattingCheck, "cargo", "fmt", "--manifest-path", "kson/Cargo.toml", "--check") {
        group = "verification"
    }

    pixiExec(formattingCheckSys, "cargo", "fmt", "--manifest-path", "kson-sys/Cargo.toml", "--check") {
        group = "verification"
    }

    named("check") {
        dependsOn(testStatic)
        dependsOn(testDynamic)
        dependsOn(formattingCheck)
    }
}