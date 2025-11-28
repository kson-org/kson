import nl.ochagavia.krossover.gradle.ReturnTypeMapping
import org.kson.BinaryArtifactPaths
import org.kson.GraalVmHelper

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
// [[kson-version-num]]
version = "0.3.0-SNAPSHOT"

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

// Build native image using GraalVM JDK from the Gradle wrapper
tasks.register<PixiExecTask>("buildWithGraalVmNativeImage") {
    group = "build"
    description = "Builds native executable using GraalVM from JDK toolchain"
    dependsOn(":kson-lib:generateJniBindingsJvm")

    val ksonLibJarTask = tasks.named<Jar>("jvmJar")
    val ksonCoreJarTask = project.rootProject.tasks.named<Jar>("jvmJar")
    dependsOn(ksonLibJarTask, ksonCoreJarTask, "generateJniBindingsJvm")

    // Configure the command at configuration time using providers
    command.set(provider {
        val graalHome = GraalVmHelper.getGraalVMHome(rootProject)

        val nativeImageExe = file("${graalHome}/bin/native-image${GraalVmHelper.getNativeImageExtension()}")
        if (!nativeImageExe.exists()) {
            throw GradleException("native-image not found at $nativeImageExe. Ensure GraalVM JDK is properly installed.")
        }

        // Ensure build dir exists
        val buildDir = project.projectDir.resolve("build/kotlin/compileGraalVmNativeImage").toPath()
        buildDir.createDirectories()
        val buildArtifactPath = buildDir.resolve(BinaryArtifactPaths.binaryFileNameWithoutExtension()).toAbsolutePath().pathString

        // Gather JAR files with the classes we use
        val ksonLibJar = ksonLibJarTask.get().archiveFile.get().asFile
        val ksonCoreJar = ksonCoreJarTask.get().archiveFile.get().asFile
        val kotlinRuntimeJarCandidates = configurations.getByName("jvmRuntimeClasspath").resolvedConfiguration.resolvedArtifacts.filter { a -> a.file.path.contains("org.jetbrains.kotlin") }
        val kotlinRuntimeJarFile = kotlinRuntimeJarCandidates[0]!!.file.absolutePath
        val jars = sequenceOf(ksonLibJar.absolutePath, ksonCoreJar.absolutePath, kotlinRuntimeJarFile)
        jars.forEach {
            if (!Path(it).toFile().exists()) {
                throw GradleException("Missing JAR file. It should have been present at $it")
            }
        }

        val cpSeparator = if (System.getProperty("os.name").lowercase().contains("win")) {
            ";"
        } else {
            ":"
        }
        val classPath = jars.joinToString(cpSeparator)

        // The `jniConfig` file tells graal which classes should be publicly exposed
        val jniConfig = project.projectDir.resolve("build/kotlin/krossover/metadata/jni-config.json")

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
