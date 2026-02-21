plugins {
    kotlin("multiplatform")
}

repositories {
    mavenCentral()
}

group = "org.kson"
// [[kson-version-num]] - base version defined in buildSrc/src/main/kotlin/org/kson/KsonVersion.kt
val isRelease = project.findProperty("release") == "true"
version = org.kson.KsonVersion.getVersion(isRelease = isRelease)
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

