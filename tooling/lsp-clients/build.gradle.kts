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

    val playwrightInstall = register<PixiExecTask>("playwrightInstall") {
        command=listOf("npx", "playwright", "install", "chromium")
        doNotTrackState("playwright already tracks its own state")
        dependsOn(npmInstall)
    }

    register<PixiExecTask>("npm_run_vscode") {
        command=listOf("npm", "run", "vscode")
        dependsOn(npmInstall)
    }

    val test = register<PixiExecTask>("npm_run_test") {
        command=listOf("npm", "run", "test")
        dependsOn(npmInstall)
        dependsOn(playwrightInstall)
        dependsOn("npm_run_buildMonacoIframe")
    }

    val buildVsCode = register<PixiExecTask>("npm_run_buildVSCode") {
        command=listOf("npm", "run", "buildVSCode")
        dependsOn(npmInstall)
    }

    val buildMonaco = register<PixiExecTask>("npm_run_buildMonaco") {
        command=listOf("npm", "run", "buildMonaco")
        dependsOn(npmInstall)
        doLast {
            val distDir = file("monaco/dist")
            val bundle = File(distDir, "index.js")
            if (bundle.exists()) {
                println()
                println("=".repeat(60))
                println("@kson/monaco-editor built successfully!")
                println("  Output: ${distDir.absolutePath}")
                println()
                println("Try it:      ./gradlew tooling:lsp-clients:npm_run_demoIframe")
                println("Full docs:   tooling/lsp-clients/monaco/readme.md")
                println("=".repeat(60))
            }
        }
    }

    val buildMonacoIframe = register<PixiExecTask>("npm_run_buildMonacoIframe") {
        command=listOf("npm", "run", "buildMonacoIframe")
        dependsOn(npmInstall)
        doLast {
            val iframeDir = file("monaco/dist-iframe")
            val editorHtml = File(iframeDir, "kson-editor.html")
            if (editorHtml.exists()) {
                println()
                println("=".repeat(60))
                println("@kson/monaco-editor iframe built successfully!")
                println("  Output: ${iframeDir.absolutePath}")
                println()
                println("Full docs:   tooling/lsp-clients/monaco/readme.md")
                println("=".repeat(60))
            }
        }
    }

    // External demos — each consumes @kson/monaco-editor as a third-party package
    // (file:../../monaco), exercising the export map and shipped types/dist artifacts.
    val demoVanillaDir = layout.projectDirectory.dir("demos/vanilla")
    val demoIframeDir = layout.projectDirectory.dir("demos/iframe")
    val demoReactDir = layout.projectDirectory.dir("demos/react")

    val installDemoVanilla = register<PixiExecTask>("npm_install_demoVanilla") {
        command=listOf("npm", "ci")
        workingDirectory.set(demoVanillaDir)
        doNotTrackState("npm already tracks its own state")
        dependsOn(buildMonaco)
    }

    register<PixiExecTask>("npm_run_demoVanilla") {
        command=listOf("npm", "run", "dev")
        workingDirectory.set(demoVanillaDir)
        dependsOn(installDemoVanilla)
    }

    val installDemoIframe = register<PixiExecTask>("npm_install_demoIframe") {
        command=listOf("npm", "ci")
        workingDirectory.set(demoIframeDir)
        doNotTrackState("npm already tracks its own state")
        dependsOn(buildMonaco)
        dependsOn(buildMonacoIframe)
    }

    register<PixiExecTask>("npm_run_demoIframe") {
        command=listOf("npm", "run", "dev")
        workingDirectory.set(demoIframeDir)
        dependsOn(installDemoIframe)
    }

    val installDemoReact = register<PixiExecTask>("npm_install_demoReact") {
        command=listOf("npm", "ci")
        workingDirectory.set(demoReactDir)
        doNotTrackState("npm already tracks its own state")
        dependsOn(buildMonaco)
    }

    register<PixiExecTask>("npm_run_demoReact") {
        command=listOf("npm", "run", "dev")
        workingDirectory.set(demoReactDir)
        dependsOn(installDemoReact)
    }

    // Single Playwright suite covering all three external-consumer demos.
    val demosDir = layout.projectDirectory.dir("demos")

    val installTestDemos = register<PixiExecTask>("npm_install_testDemos") {
        command=listOf("npm", "ci")
        workingDirectory.set(demosDir)
        doNotTrackState("npm already tracks its own state")
        dependsOn(buildMonaco)
        dependsOn(buildMonacoIframe)
        dependsOn(installDemoVanilla)
        dependsOn(installDemoIframe)
        dependsOn(installDemoReact)
    }

    val playwrightInstallTestDemos = register<PixiExecTask>("playwrightInstall_testDemos") {
        command=listOf("npx", "playwright", "install", "chromium")
        workingDirectory.set(demosDir)
        doNotTrackState("playwright already tracks its own state")
        dependsOn(installTestDemos)
    }

    val testDemos = register<PixiExecTask>("npm_run_testDemos") {
        command=listOf("npm", "test")
        workingDirectory.set(demosDir)
        dependsOn(playwrightInstallTestDemos)
    }

    check {
        dependsOn(test)
        dependsOn(testDemos)
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
        delete("monaco/dist-iframe")
        delete("shared/out")
        delete("demos/vanilla/node_modules")
        delete("demos/iframe/node_modules")
        delete("demos/react/node_modules")
        delete("demos/node_modules")
        delete("demos/test-results")
        delete("demos/playwright-report")
    }
}
