plugins {
    base
}

tasks {
    val npmInstall = register<PixiExecTask>("npmInstall") {
        command=listOf("pnpm", "install", "--frozen-lockfile")
        dependsOn(":kson-lib:jsNodeProductionLibraryDistribution")
        dependsOn(":kson-tooling-lib:jsNodeProductionLibraryDistribution")
        doNotTrackState("npm already tracks its own state")
    }

    register<PixiExecTask>("npm_run_compile") {
        command=listOf("pnpm", "run", "compile")
        dependsOn(npmInstall)
    }

    register<PixiExecTask>("npm_run_test") {
        command=listOf("pnpm", "run", "test")
        dependsOn("npm_run_compile")
    }

    check {
        dependsOn("npm_run_test")
    }

    clean {
        delete("out")
        delete("node_modules")
    }
}
