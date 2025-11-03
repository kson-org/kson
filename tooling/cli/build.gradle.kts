import org.kson.GraalVmHelper

plugins {
    application
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.github.ajalt.clikt:clikt:5.0.1")
    implementation(project(":kson-lib"))

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
}

application {
    mainClass.set("org.kson.tooling.cli.CommandLineInterfaceKt")
}

// Custom task to build native image using GraalVM JDK
val buildNativeImage by tasks.registering(PixiExecTask::class) {
    group = "build"
    description = "Builds native executable using GraalVM from JDK toolchain"

    dependsOn(tasks.jar)

    val outputDir = layout.buildDirectory.dir("native/nativeCompile").get().asFile
    val outputFile = file("$outputDir/kson")

    // Configure the command at configuration time using providers
    command.set(provider {
        val graalHome = GraalVmHelper.getGraalVMHome(rootProject)

        val nativeImageExe = file("${graalHome}/bin/native-image${GraalVmHelper.getNativeImageExtension()}")
        if (!nativeImageExe.exists()) {
            throw GradleException("native-image not found at $nativeImageExe. Ensure GraalVM JDK is properly installed.")
        }

        val classpath = configurations.runtimeClasspath.get().asPath
        val jarFile = tasks.jar.get().archiveFile.get().asFile

        listOf(
            nativeImageExe.absolutePath,
            "-cp", "${jarFile.absolutePath}${File.pathSeparator}$classpath",
            "-H:+ReportExceptionStackTraces",
            "--no-fallback",
            "-o", outputFile.absolutePath,
            "org.kson.tooling.cli.CommandLineInterfaceKt"
        )
    })

    doFirst {
        // Create output directory
        outputDir.mkdirs()

        println("Building native image with GraalVM using pixi")
        println("Creating executable: $outputFile")
    }

    doLast {
        if (outputFile.exists()) {
            println("\n✅ Native image built successfully!")
            println("   Executable: $outputFile")
            println("   Size: ${outputFile.length() / 1024 / 1024} MB")
            println("\nRun it with: $outputFile --help")
        }
    }
}

tasks{
    check {
        dependsOn(buildNativeImage)
    }
}
