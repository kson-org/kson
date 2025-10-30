import nl.ochagavia.krossover.gradle.ReturnTypeMapping
import org.kson.BinaryArtifactPaths

import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString

plugins {
    kotlin("multiplatform")
    id("com.vanniktech.maven.publish") version "0.30.0"
    id("org.jetbrains.dokka") version "2.0.0"
    id("nl.ochagavia.krossover") version "1.0.4"
}

repositories {
    mavenCentral()
}

group = "org.kson"
version = "0.1.2-SNAPSHOT"

kotlin {
    jvm {
        testRuns["test"].executionTask.configure {
            useJUnit()
        }
    }
    js(IR) {
        browser()
        nodejs()
        binaries.library()
        useEsModules()
        generateTypeScriptDefinitions()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

krossover {
    libName = "kson"
    rootClasses = listOf("org.kson.Kson")
    exposedPackages = listOf("org.kson")

    jniHeaderOutputFile = project.projectDir.resolve("build/kotlin/compileGraalVmNativeImage/jni_simplified.h").toPath()

    python {
        outputDir = Path("${rootProject.projectDir}/lib-python/src/kson")
    }

    rust {
        jniSysModule = "kson_sys"
        outputDir = Path("${rootProject.projectDir}/lib-rust/kson/src/generated")
        returnTypeMappings = listOf(
            ReturnTypeMapping("org.kson.Result", "std::result::Result<result::Success, result::Failure>", "crate::kson_result_into_rust_result"),
            ReturnTypeMapping("org.kson.SchemaResult", "std::result::Result<schema_result::Success, schema_result::Failure>", "crate::kson_schema_result_into_rust_result")
        )
    }
}

// Task to copy browser distribution after building
tasks.register("copyBrowserDistribution") {
    description = "Copy browser JS distribution to js-package/browser"
    dependsOn("jsBrowserProductionLibraryDistribution")

    doLast {
        val buildDir = layout.buildDirectory.get().asFile
        val sourceDir = buildDir.resolve("dist/js/productionLibrary")
        val targetDir = buildDir.resolve("js-package/browser")

        targetDir.mkdirs()
        copy {
            from(sourceDir)
            into(targetDir)
        }
        println("Copied browser distribution to: ${targetDir.absolutePath}")
    }
}

// Task to copy Node.js distribution after building
tasks.register("copyNodeDistribution") {
    description = "Copy Node.js distribution to js-package/node"
    dependsOn("jsNodeProductionLibraryDistribution")

    doLast {
        val buildDir = layout.buildDirectory.get().asFile
        val sourceDir = buildDir.resolve("dist/js/productionLibrary")
        val targetDir = buildDir.resolve("js-package/node")

        targetDir.mkdirs()
        copy {
            from(sourceDir)
            into(targetDir)
        }
        println("Copied Node.js distribution to: ${targetDir.absolutePath}")
    }
}

// Configure task ordering to ensure sequential execution
afterEvaluate {
    tasks.named("jsNodeProductionLibraryDistribution") {
        mustRunAfter("copyBrowserDistribution")
    }
}

// Main task to build universal JS package
tasks.register("buildUniversalJsPackage") {
    description = "Build universal JS package with browser and Node.js distributions"
    group = "build"

    // First build browser and copy it
    dependsOn("copyBrowserDistribution")

    // Then build node (this will overwrite dist/js/productionLibrary)
    // but we'll copy it after browser is already saved
    dependsOn("copyNodeDistribution")

    doLast {
        val buildDir = layout.buildDirectory.get().asFile
        val jsPackageDir = buildDir.resolve("js-package")

        // Ensure directory exists
        jsPackageDir.mkdirs()


        // Copy TypeScript definitions (from browser, they should be the same)
        copy {
            from(jsPackageDir.resolve("browser"))
            include("*.d.ts")
            into(jsPackageDir)
        }

        // Write universal package.json
        val packageJson = """
        {
          "name": "@kson_org/kson",
          "version": "0.1.0",
          "description": "KSON - Extended JSON format with comments and more",
          "author": {
            "name": "KSON Team",
            "email": "kson@kson.org"
          },
          "repository": {
            "type": "git",
            "url": "https://github.com/kson-org/kson"
          },
          "license": "Apache-2.0",
          "keywords": ["json", "kson", "yaml", "configuration"],
          "exports": {
            ".": {
              "browser": "./browser/kson-kson-lib.mjs",
              "node": "./node/kson-kson-lib.mjs",
              "types": "./kson-kson-lib.d.ts"
            }
          },
          "main": "./node/kson-kson-lib.mjs",
          "browser": "./browser/kson-kson-lib.mjs",
          "types": "./kson-kson-lib.d.ts",
          "files": [
            "browser/",
            "node/",
            "*.d.ts",
            "README.md"
          ]
        }
        """.trimIndent()

        jsPackageDir.resolve("package.json").writeText(packageJson)

        // Copy README if it exists
        val readmeFile = projectDir.resolve("README-npm.md")
        if (readmeFile.exists()) {
            copy {
                from(readmeFile)
                into(jsPackageDir)
                rename { "README.md" }
            }
        }

        // Copy LICENSE if it exists
        val licenseFile = rootDir.resolve("LICENSE")
        if (licenseFile.exists()) {
            copy {
                from(licenseFile)
                into(jsPackageDir)
            }
        }

        println("Universal JS package built successfully at: ${jsPackageDir.absolutePath}")
    }
}

mavenPublishing {
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL, automaticRelease = false)
    signAllPublications()

    coordinates("org.kson", "kson", version.toString())

    pom {
        name.set("KSON")
        description.set("A 💌 to the humans maintaining computer configurations")
        url.set("https://kson.org")

        licenses {
            license {
                name.set("Apache-2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }

        developers {
            developer {
                id.set("dmarcotte")
                name.set("Daniel Marcotte")
                email.set("kson@kson.org")
            }
        }

        scm {
            connection.set("scm:git:https://github.com/kson-org/kson.git")
            developerConnection.set("scm:git:git@github.com:kson-org/kson.git")
            url.set("https://github.com/kson-org/kson")
        }
    }
}


// GraalVM configuration
val graalvmVersion = "25"
val graalvmSemVer = "25.0.0"
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
tasks.register<PixiExecTask>("buildWithGraalVmNativeImage") {
    group = "build"
    description = "Builds native executable using GraalVM $graalvmSemVer"
    dependsOn(":kson-lib:generateJniBindingsJvm")

    val jvmTask = tasks.named<Jar>("jvmJar")
    dependsOn(downloadGraalVM, jvmTask, "generateJniBindingsJvm")

    // Configure the command at configuration time using providers
    command.set(provider {
        val graalHome = getGraalVMHome()
            ?: throw GradleException("GraalVM not found! Please run './gradlew :tooling:cli:downloadGraalVM' first")

        val nativeImageExe = file("${graalHome}/bin/native-image" + getGraalNativeImageExtension())
        if (!nativeImageExe.exists()) {
            throw GradleException("native-image not found at $nativeImageExe")
        }

        // Ensure build dir exists
        val buildDir = project.projectDir.resolve("build/kotlin/compileGraalVmNativeImage").toPath()
        buildDir.createDirectories()

        val buildArtifactPath = buildDir.resolve(BinaryArtifactPaths.binaryFileNameWithoutExtension()).toAbsolutePath().pathString
        val jniConfig = project.projectDir.resolve("build/kotlin/krossover/metadata/jni-config.json")
        val ksonCoreJar = rootDir.resolve("build/libs/kson-jvm-x.2-SNAPSHOT.jar")

        val jarFile = jvmTask.get().archiveFile.get().asFile
        val kotlinRuntimeJarCandidates = configurations.getByName("jvmRuntimeClasspath").resolvedConfiguration.resolvedArtifacts.filter { a -> a.file.path.contains("org.jetbrains.kotlin") }
        val kotlinRuntimeJarFile = kotlinRuntimeJarCandidates[0]!!.file.absolutePath

        val cpSeparator = if (System.getProperty("os.name").lowercase().contains("win")) {
            ";"
        } else {
            ":"
        }
        val classPath = sequenceOf(jarFile.absolutePath, ksonCoreJar, kotlinRuntimeJarFile).joinToString(cpSeparator)

        listOf(
            nativeImageExe.absolutePath,
            "--shared",
            "-cp", classPath,
            "-H:+UnlockExperimentalVMOptions", // Necessary to use JNIConfigurationFiles option below
            "-H:JNIConfigurationFiles=$jniConfig",
            "-o", buildArtifactPath
        )
    })
}
