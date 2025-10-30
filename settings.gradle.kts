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
include("kson-lib")
include("lib-python")
include("lib-rust")
include("tooling:jetbrains")
include("tooling:language-server-protocol")
include("tooling:lsp-clients")
include("tooling:cli")
