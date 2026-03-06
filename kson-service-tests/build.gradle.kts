plugins {
    kotlin("multiplatform")
}

repositories {
    mavenCentral()
}

kotlin {
    jvm()
    js(IR) {
        browser()
        nodejs()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":kson-service-api"))
                api(kotlin("test"))
            }
        }

        val jvmMain by getting {
            dependencies {
                api(kotlin("test-junit5"))
            }
        }
    }
}
