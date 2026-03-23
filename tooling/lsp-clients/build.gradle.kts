plugins {
    base
}

tasks {
    val npmInstall = register<PixiExecTask>("npmInstall") {
        // Use `npm ci` for reproducible installs from the lock file
        command=listOf("npm", "ci")
        dependsOn(":tooling:language-server-protocol:npm_run_test")

        inputs.file("package.json")
        inputs.file("package-lock.json")
        // Workspace packages have file: dependencies on the language server
        inputs.dir(project(":tooling:language-server-protocol").file("out"))
        inputs.file(project(":tooling:language-server-protocol").file("package.json"))
        outputs.file(layout.buildDirectory.file("stamp/npmInstall.stamp"))
        doLast { layout.buildDirectory.file("stamp/npmInstall.stamp").get().asFile.apply { parentFile.mkdirs(); writeText("${System.currentTimeMillis()}") } }
    }

    val playwrightInstall = register<PixiExecTask>("playwrightInstall") {
        command=listOf("npx", "playwright", "install", "chromium")
        doNotTrackState("playwright already tracks its own state")
        dependsOn(npmInstall)
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
        dependsOn(playwrightInstall)

        inputs.dir("vscode/src")
        inputs.dir("shared/src")
        inputs.dir("monaco/src")
        inputs.dir("monaco/tests")
        inputs.file("package.json")
        inputs.file("tsconfig.json")
        outputs.file(layout.buildDirectory.file("stamp/npm_run_test.stamp"))
        doLast { layout.buildDirectory.file("stamp/npm_run_test.stamp").get().asFile.apply { parentFile.mkdirs(); writeText("${System.currentTimeMillis()}") } }
    }

    val buildVsCode = register<PixiExecTask>("npm_run_buildVSCode") {
        command=listOf("npm", "run", "buildVSCode")
        dependsOn(npmInstall)

        inputs.dir("vscode/src")
        inputs.dir("shared/src")
        inputs.file("package.json")
        inputs.file("tsconfig.json")
        // Use a stamp file rather than outputs.dir("vscode/dist") because the
        // test task modifies vscode/dist, which would invalidate this task's cache
        outputs.file(layout.buildDirectory.file("stamp/npm_run_buildVSCode.stamp"))
        doLast { layout.buildDirectory.file("stamp/npm_run_buildVSCode.stamp").get().asFile.apply { parentFile.mkdirs(); writeText("${System.currentTimeMillis()}") } }
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
