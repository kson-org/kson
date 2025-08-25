import org.gradle.internal.os.OperatingSystem

val copyKotlinSource = "copyKotlinSource"
val formattingCheck = "formattingCheck"
val testStatic = "testStaticallyLinked"
val testDynamic = "testDynamicallyLinked"

tasks {
    val pixiwPath = if (OperatingSystem.current().isWindows) {
        "pixiw"
    } else {
        "./pixiw"
    }

    register<CopyNativeHeaderTask>("copyHeaderFileDynamic") {
        dependsOn(":lib-kotlin:nativeKsonBinaries")
        useDynamicLinking = true
        sourceProjectDir = project.projectDir.resolve("kotlin")
        outputDir = File("artifacts")
    }

    register<CopyNativeHeaderTask>("copyHeaderFileStatic") {
        dependsOn(":lib-kotlin:nativeKsonBinaries")
        useDynamicLinking = false
        sourceProjectDir = project.projectDir.resolve("kotlin")
        outputDir = File("artifacts")
    }

    register<CopyRepositoryFilesTask>(copyKotlinSource) {
        group = "build setup"
        description = "Copies all repository files (except git-ignored and lib-rust) to ./kotlin"
        excludedPath = "lib-rust/"
    }

    register<Exec>(testStatic) {
        dependsOn(copyKotlinSource)

        group = "verification"
        commandLine = "$pixiwPath run cargo test".split(" ")
        standardOutput = System.out
        errorOutput = System.err
        isIgnoreExitValue = false
        onlyIf { !OperatingSystem.current().isWindows }
    }

    register<Exec>(testDynamic) {
        dependsOn(copyKotlinSource)

        group = "verification"
        commandLine = "$pixiwPath run cargo test --features dynamic-linking".split(" ")
        standardOutput = System.out
        errorOutput = System.err
        isIgnoreExitValue = false
    }

    register<Exec>(formattingCheck) {
        group = "verification"
        commandLine = "$pixiwPath run cargo fmt --check".split(" ")
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
