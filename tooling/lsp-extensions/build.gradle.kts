plugins {
    base
    id("com.github.node-gradle.node") version "7.1.0"
}

node {
    version.set("20.19.0")
    npmVersion.set("10.8.2")
    download.set(true)
}

tasks {
    npmInstall.configure {
        dependsOn(":tooling:language-server-protocol:npm_run_test")
    }

    named("npm_run_vscode") {}
    named("npm_run_monaco") {}

    named("check") {
        dependsOn("npm_run_test")
        dependsOn("npm_run_buildPlugins")
    }
}