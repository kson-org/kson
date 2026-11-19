plugins {
    base
}

tasks {
    val npmInstall = register<PixiExecTask>("npm_install") {
        // updates pnpm-lock.yaml files whenever package.json changes
        command=listOf("pnpm", "install", "--no-frozen-lockfile")
        dependsOn(":kson-lib:jsNodeProductionLibraryDistribution")
        dependsOn(":kson-tooling-lib:jsNodeProductionLibraryDistribution")
        doNotTrackState("pnpm already tracks its own state")
    }

    register("npmInstall") { // deprecated alias to npm_install
        dependsOn(npmInstall)
    }

    register<PixiExecTask>("npm_run_compile") {
        command=listOf("pnpm", "run", "compile")
        dependsOn(npmInstall)
    }

    register<PixiExecTask>("npm_run_test") {
        command=listOf("pnpm", "run", "test")
        dependsOn("npm_run_compile")
    }

    register<PixiExecTask>("npm_run_package") {
        command=listOf("pnpm", "run", "package")
        dependsOn(npmInstall)
    }

    check {
        dependsOn("npm_run_test")
    }

    clean {
        delete("out")
        delete("dist")
        delete("node_modules")
    }
}
