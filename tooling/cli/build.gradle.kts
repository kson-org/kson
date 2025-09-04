import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption

plugins {
    application
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.github.ajalt.clikt:clikt:5.0.1")
    implementation(project(":lib-kotlin"))

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
}

application {
    mainClass.set("org.kson.tooling.cli.CommandLineInterfaceKt")
}

// GraalVM configuration
val graalvmVersion = "21"
val graalvmSemVer = "21.0.2"
val graalvmDir = file("${rootProject.projectDir}/gradle/jdk")

/**
 * Determines the GraalVM platform identifier based on the current system's OS and architecture.
 * @return A platform string in the format "{os}-{arch}" (e.g., "macos-aarch64", "linux-amd64")
 * @throws GradleException if the OS or architecture is unsupported
 */
fun getGraalVMPlatform(): String {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()

    val osName = when {
        os.contains("mac") || os.contains("darwin") -> "macos"
        os.contains("win") -> "windows"
        os.contains("linux") -> "linux"
        else -> throw GradleException("Unsupported OS: $os")
    }

    val archName = when {
        arch.contains("aarch64") || arch.contains("arm64") -> "aarch64"
        arch.contains("x86_64") || arch.contains("amd64") -> "x64"
        else -> throw GradleException("Unsupported architecture: $arch")
    }

    return "${osName}-${archName}"
}

/**
 * Determines the file extension for GraalVM downloads based on the current operating system.
 * @return A file extension string: "zip" for Windows, "tar.gz" for macOS/Linux
 * @throws GradleException if the OS is unsupported
 */
fun getGraalVMExtension(): String {
    val os = System.getProperty("os.name").lowercase()

    return when {
        os.contains("win") -> "zip"
        os.contains("mac") || os.contains("darwin") || os.contains("linux") -> "tar.gz"
        else -> throw GradleException("Unsupported OS: $os")
    }
}

/**
 * Determines the file extension for the native-image executable based on the operating system.
 * @return A file extension string: ".cmd" for Windows, empty string for macOS/Linux
 * @throws GradleException if the OS is unsupported
 */
fun getGraalNativeImageExtension(): String {
    val os = System.getProperty("os.name").lowercase()

    return when {
        os.contains("win") -> ".cmd"
        os.contains("mac") || os.contains("darwin") || os.contains("linux") -> ""
        else -> throw GradleException("Unsupported OS: $os")
    }
}

/**
 * Locates the GraalVM installation directory within the gradle/jdk folder.
 * Handles platform-specific directory structures (e.g., macOS's Contents/Home subdirectory).
 * @return The GraalVM home directory if found, null otherwise
 */
fun getGraalVMHome(): File? {
    val existingGraalVM = graalvmDir.listFiles()?.find {
        it.isDirectory && it.name.contains("graalvm") && it.name.contains(graalvmVersion)
    } ?: return null

    val platform = getGraalVMPlatform()
    return if (platform.startsWith("macos") && file("$existingGraalVM/Contents/Home").exists()) {
        file("$existingGraalVM/Contents/Home")
    } else {
        existingGraalVM
    }
}

val downloadGraalVM by tasks.registering {
    group = "build setup"
    description = "Downloads GraalVM $graalvmSemVer for native image compilation"

    doLast {
        if (getGraalVMHome() != null) {
            println("GraalVM already exists at: ${getGraalVMHome()}")
            return@doLast
        }

        graalvmDir.mkdirs()

        val platform = getGraalVMPlatform()
        val extension = getGraalVMExtension()
        val downloadUrl =
            "https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-${graalvmSemVer}/graalvm-community-jdk-${graalvmSemVer}_${platform}_bin.$extension"
        val tempFile = file("$graalvmDir/graalvm-download.$extension")

        println("Downloading GraalVM from: $downloadUrl")

        URL(downloadUrl).openStream().use { input ->
            Files.copy(input, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }

        println("Extracting GraalVM...")

        exec {
            commandLine("tar", "-xzf", tempFile.absolutePath, "-C", graalvmDir.absolutePath)
        }

        tempFile.delete()

        println("GraalVM installed to: ${getGraalVMHome()}")
    }
}

// Custom task to build native image using downloaded GraalVM
val buildNativeImage by tasks.registering(Exec::class) {
    group = "build"
    description = "Builds native executable using GraalVM $graalvmSemVer"

    dependsOn(downloadGraalVM, tasks.jar)

    val outputDir = layout.buildDirectory.dir("native/nativeCompile").get().asFile
    val outputFile = file("$outputDir/kson")

    doFirst {
        // Determine pixi wrapper path
        val pixiwPath = if (System.getProperty("os.name").lowercase().contains("win")) {
            "pixiw"
        } else {
            "./pixiw"
        }
        
        // Check if pixi wrapper exists
        val pixiwFile = file(pixiwPath)
        val usePixi = pixiwFile.exists()

        val graalHome = getGraalVMHome()
        if (graalHome == null || !graalHome.exists()) {
            throw GradleException("GraalVM not found! Please run './gradlew :tooling:cli:downloadGraalVM' first")
        }

        val nativeImageExe = file("${graalHome}/bin/native-image" + getGraalNativeImageExtension())
        if (!nativeImageExe.exists()) {
            throw GradleException("native-image not found at $nativeImageExe")
        }

        // Build the classpath
        val classpath = configurations.runtimeClasspath.get().asPath
        val jarFile = tasks.jar.get().archiveFile.get().asFile

        // Create output directory
        outputDir.mkdirs()
        
        if (usePixi) {
            println("Building native image with GraalVM using pixi")
            println("Creating executable: $outputFile")
            
            // Use pixi to run native-image
            commandLine = listOf(
                pixiwPath, "run",
                nativeImageExe.absolutePath,
                "-cp", "${jarFile.absolutePath}${File.pathSeparator}$classpath",
                "-H:+ReportExceptionStackTraces",
                "--no-fallback",
                "-o", outputFile.absolutePath,
                "org.kson.tooling.cli.CommandLineInterfaceKt"
            )
        } else {
            throw UnsupportedOperationException("Something is off. Did the installation install pixi to $pixiwFile? Or GraalVM to $graalvmDir?")
        }
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
