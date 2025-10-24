import org.jetbrains.kotlin.konan.target.Architecture
import org.jetbrains.kotlin.konan.target.Family
import org.jetbrains.kotlin.konan.target.HostManager

plugins {
    kotlin("multiplatform")
    id("com.vanniktech.maven.publish") version "0.30.0"
    id("org.jetbrains.dokka") version "2.0.0"
}

repositories {
    mavenCentral()
}

group = "org.kson"
version = "0.1.2-SNAPSHOT"

tasks {
    val copyHeaderDynamic = register<CopyNativeHeaderTask>("copyNativeHeaderDynamic") {
        dependsOn(":kson-lib:nativeKsonBinaries")
        useDynamicLinking = true
        outputDir = project.projectDir.resolve("build/nativeHeaders")
    }

    val copyHeaderStatic = register<CopyNativeHeaderTask>("copyNativeHeaderStatic") {
        dependsOn(":kson-lib:nativeKsonBinaries")
        useDynamicLinking = false
        outputDir = project.projectDir.resolve("build/nativeHeaders")
    }

    register<Task>("nativeRelease") {
        dependsOn(":kson-lib:nativeKsonBinaries", copyHeaderDynamic, copyHeaderStatic)
    }
}

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

    val host = HostManager.host
    val nativeTarget = when (host.family) {
        Family.OSX -> when (host.architecture) {
            Architecture.ARM64 -> macosArm64("nativeKson")
            else -> macosX64("nativeKson")
        }
        Family.LINUX -> linuxX64("nativeKson")
        Family.MINGW -> mingwX64("nativeKson")
        Family.IOS, Family.TVOS, Family.WATCHOS, Family.ANDROID -> {
            throw GradleException("Host OS '${host.name}' is not supported in Kotlin/Native.")
        }
    }

    nativeTarget.apply {
        binaries {
            sharedLib {
                baseName = "kson"
            }

            staticLib {
                baseName = "kson"
            }
        }
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
