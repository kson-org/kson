plugins {
    base
    id("com.github.node-gradle.node") version "7.1.0"
}

node {
     version.set("20.0.0")
     npmVersion.set("9.6.4")
     download.set(true)
}

tasks {
    named("npm_run_compile"){
        dependsOn(":kson-lib:jsNodeProductionLibraryDistribution")
        dependsOn(npmInstall)
    }

    named("npm_run_test"){
        dependsOn("npm_run_compile")
    }

    check {
        dependsOn("npm_run_test")
    }
}
