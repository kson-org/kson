plugins {
    kotlin("multiplatform")
}

repositories {
    mavenCentral()
}

tasks.withType<Test> {
    dependsOn(":lib-python:build")

    systemProperty("libPythonDir", project(":lib-python").projectDir.absolutePath)

    useJUnitPlatform()
    jvmArgs("-Djunit.jupiter.extensions.autodetection.enabled=true")
}

kotlin {
    jvm()

    sourceSets {
        commonTest {
            dependencies {
                implementation(project(":kson-service-tests"))
                implementation(project(":kson-http"))
            }
        }
        jvmTest {
            dependencies {
                implementation(kotlin("test-junit5"))

                // Important: this ensures we have a recent-enough version of JUnit, supporting the `AutoCloseable`
                // interface (otherwise test runs never finish because the HTTP server doesn't get closed)
                implementation(project.dependencies.platform("org.junit:junit-bom:5.14.3"))
            }
        }
    }
}

