plugins {
    base
}

tasks {
    val npmInstall = register<PixiExecTask>("npmInstall") {
        // Use `npm ci` for reproducible installs from the lock file
        command=listOf("npm", "ci")
        dependsOn(":kson-lib:jsNodeProductionLibraryDistribution")
        dependsOn(":kson-tooling-lib:jsNodeProductionLibraryDistribution")

        inputs.file("package.json")
        inputs.file("package-lock.json")
        // package.json has file: dependencies on these production libraries
        inputs.dir(project(":kson-lib").layout.buildDirectory.dir("dist/js/productionLibrary"))
        inputs.dir(project(":kson-tooling-lib").layout.buildDirectory.dir("dist/js/productionLibrary"))
        outputs.file(layout.buildDirectory.file("stamp/npmInstall.stamp"))
        doLast { layout.buildDirectory.file("stamp/npmInstall.stamp").get().asFile.apply { parentFile.mkdirs(); writeText("${System.currentTimeMillis()}") } }
    }

    register<PixiExecTask>("npm_run_compile") {
        command=listOf("npm", "run", "compile")
        dependsOn(npmInstall)

        inputs.dir("src")
        inputs.file("tsconfig.json")
        inputs.file("package.json")
        outputs.dir("out")
    }

    register<PixiExecTask>("npm_run_test") {
        command=listOf("npm", "run", "test")
        dependsOn("npm_run_compile")

        inputs.dir("src")
        inputs.dir("out")
        inputs.dir("examples")
        inputs.file("package.json")
        outputs.file(layout.buildDirectory.file("stamp/npm_run_test.stamp"))
        doLast { layout.buildDirectory.file("stamp/npm_run_test.stamp").get().asFile.apply { parentFile.mkdirs(); writeText("${System.currentTimeMillis()}") } }
    }

    check {
        dependsOn("npm_run_test")
    }

    clean {
        delete("out")
        delete("node_modules")
    }
}
