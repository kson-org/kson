import org.gradle.internal.os.OperatingSystem

val copyKotlinSource = "copyKotlinSource"
val formattingCheck = "formattingCheckKson"
val formattingCheckSys = "formattingCheckKsonSys"
val testStatic = "testStaticallyLinked"
val testDynamic = "testDynamicallyLinked"

tasks {
    val pixiwPath = if (OperatingSystem.current().isWindows) {
        "pixiw"
    } else {
        "./pixiw"
    }

    register<CopyRepositoryFilesTask>(copyKotlinSource) {
        group = "build setup"
        description = "Copies all repository files (except git-ignored and lib-rust) to ./kotlin"
        excludedPath = "lib-rust/kson-sys"
        outputDir = project.projectDir.resolve("kson-sys/kotlin")
    }

    register<Exec>(testStatic) {
        dependsOn(copyKotlinSource)

        group = "verification"
        commandLine = "$pixiwPath run cargo test --manifest-path kson/Cargo.toml".split(" ")
        standardOutput = System.out
        errorOutput = System.err
        isIgnoreExitValue = false
        onlyIf { !OperatingSystem.current().isWindows }
    }

    register<Exec>(testDynamic) {
        dependsOn(copyKotlinSource)

        group = "verification"
        commandLine = "$pixiwPath run cargo test --manifest-path kson/Cargo.toml --features dynamic-linking".split(" ")
        standardOutput = System.out
        errorOutput = System.err
        isIgnoreExitValue = false
    }

    register<Exec>(formattingCheck) {
        group = "verification"
        commandLine = "$pixiwPath run cargo fmt --manifest-path kson/Cargo.toml --check".split(" ")
        standardOutput = System.out
        errorOutput = System.err
        isIgnoreExitValue = false
    }

    register<Exec>(formattingCheckSys) {
        group = "verification"
        commandLine = "$pixiwPath run cargo fmt --manifest-path kson-sys/Cargo.toml --check".split(" ")
        standardOutput = System.out
        errorOutput = System.err
        isIgnoreExitValue = false
    }

    register<Task>("check") {
        group = "verification"
        dependsOn(testStatic)
        dependsOn(testDynamic)
        dependsOn(formattingCheck)
    }
}
