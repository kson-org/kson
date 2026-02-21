plugins {
    kotlin("multiplatform")
}

repositories {
    mavenCentral()
}

val syncCommonTestSources by tasks.registering(Sync::class) {
    from(project(":kson-lib").file("src/commonTest/kotlin"))
    into(layout.buildDirectory.dir("commonTestSources"))
}

val syncJvmTestSources by tasks.registering(Sync::class) {
    from(project(":kson-lib").file("src/jvmTest/kotlin"))
    into(layout.buildDirectory.dir("jvmTestSources"))
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
                implementation(project(":kson-lib-http"))
                implementation(kotlin("test"))
            }

            kotlin {
                srcDir(syncCommonTestSources)
            }
        }
        jvmTest {
            dependencies {
                implementation("org.junit.jupiter:junit-jupiter-api:5.14.2")
                runtimeOnly("org.junit.jupiter:junit-jupiter-engine:5.14.2")
            }

            kotlin {
                srcDir(syncJvmTestSources)
            }
        }
    }
}

