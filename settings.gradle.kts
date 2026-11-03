pluginManagement {
    plugins {
        kotlin("multiplatform") version "2.4.21"
        kotlin("jvm") version "2.4.21"
        kotlin("plugin.serialization") version "2.4.21"
    }

    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "kson"
include("native-tests")
include("kson-lib")
include("kson-tooling-lib")
include("lib-python")
include("lib-rust")
include("tooling:jetbrains")
include("tooling:language-server-protocol")
include("tooling:lsp-clients")
include("tooling:cli")

buildCache {
    local {
        // Release artifacts must not come out of the build cache; a release build neither loads nor stores entries
        isEnabled = providers.gradleProperty("release").orNull != "true"
    }
}
