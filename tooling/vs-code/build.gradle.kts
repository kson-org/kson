plugins {
    base
    id("com.github.node-gradle.node") version "7.1.0"
}

node {
     version.set("20.18.1")
     npmVersion.set("10.8.2")
     download.set(true)
}

tasks {
    named("npm_run_testPlugin"){
        dependsOn(npmInstall)
        dependsOn(":tooling:language-server-protocol:npm_run_test")
    }

    named("npm_run_vscode"){
        dependsOn(npmInstall)
    }

    named("check") {
        dependsOn("npm_run_testPlugin")
        dependsOn("npm_run_packagePlugin")
    }
}