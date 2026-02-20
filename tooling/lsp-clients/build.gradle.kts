plugins {
    base
}

tasks {
    val npmInstall = register<PixiExecTask>("npmInstall") {
        // Use `npm ci` for reproducible installs from the lock file
        command=listOf("npm", "ci")
        doNotTrackState("npm already tracks its own state")
        dependsOn(":tooling:language-server-protocol:npm_run_test")
    }

    register<PixiExecTask>("npm_run_vscode") {
        command=listOf("npm", "run", "vscode")
        dependsOn(npmInstall)
    }

    register<PixiExecTask>("npm_run_monaco") {
        command=listOf("npm", "run", "monaco")
        dependsOn(npmInstall)
    }

    val test = register<PixiExecTask>("npm_run_test") {
        command=listOf("npm", "run", "test")
        dependsOn(npmInstall)
    }

    val buildVsCode = register<PixiExecTask>("npm_run_buildVSCode") {
        command=listOf("npm", "run", "buildVSCode")
        dependsOn(npmInstall)
    }

    val buildMonaco = register<PixiExecTask>("npm_run_buildMonaco") {
        command=listOf("npm", "run", "buildMonaco")
        dependsOn(npmInstall)
    }

    check {
        dependsOn(test)
        /**
         * TODO - Ideally this task is "npm_run_buildPlugins" building both plugins, however, for now the Monaco Vite build
         * is too unpredictable in CI
         */
        dependsOn(buildVsCode)
    }

    clean {
        delete("node_modules")
        delete("vscode/out")
        delete("vscode/dist")
        delete("vscode/node_modules")
        delete("monaco/dist")
        delete("monaco/node_modules")
        delete("shared/out")
    }
}
