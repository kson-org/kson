pluginManagement {
    plugins {
        kotlin("multiplatform") version "2.2.20"
        kotlin("jvm") version "2.2.20"
        kotlin("plugin.serialization") version "2.2.20"
    }

    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "kson"
include("native-tests")
include("kson-service-api")
include("kson-service-tests")
include("kson-lib")
include("kson-http")
include("kson-tooling-lib")
include("lib-python")
include("lib-python:kson-lib-tests")
include("lib-rust")
include("lib-rust:kson-lib-tests")
include("tooling:jetbrains")
include("tooling:language-server-protocol")
include("tooling:lsp-clients")
include("tooling:cli")
