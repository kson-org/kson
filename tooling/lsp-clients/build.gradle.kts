plugins {
    base
}

tasks {
    val npmInstall = register<PixiExecTask>("npmInstall") {
        // `--frozen-lockfile` gives reproducible installs from pnpm-lock.yaml;
        // hoisted keeps a flat node_modules (npm-identical layout) while still
        // hardlinking from pnpm's shared global store.
        command=listOf("pnpm", "install", "--frozen-lockfile", "--config.node-linker=hoisted")
        doNotTrackState("pnpm already tracks its own state")
        dependsOn(":tooling:language-server-protocol:npm_run_test")
    }

    val playwrightInstall = register<PixiExecTask>("playwrightInstall") {
        command=listOf("pnpm", "exec", "playwright", "install", "chromium")
        doNotTrackState("playwright already tracks its own state")
        dependsOn(npmInstall)
    }

    register<PixiExecTask>("npm_run_vscode") {
        command=listOf("pnpm", "run", "vscode")
        dependsOn(npmInstall)
    }

    val test = register<PixiExecTask>("npm_run_test") {
        command=listOf("pnpm", "run", "test")
        dependsOn(npmInstall)
        dependsOn(playwrightInstall)
        dependsOn("npm_run_buildMonacoIframe")
    }

    val buildVsCode = register<PixiExecTask>("npm_run_buildVSCode") {
        command=listOf("pnpm", "run", "buildVSCode")
        dependsOn(npmInstall)
    }

    val buildMonaco = register<PixiExecTask>("npm_run_buildMonaco") {
        command=listOf("pnpm", "run", "buildMonaco")
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
        command=listOf("pnpm", "run", "buildMonacoIframe")
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

    // `--ignore-workspace` keeps each demo a standalone install so it consumes the
    // PACKED @kson/monaco-editor (file:), not a workspace symlink to source.
    val installDemoVanilla = register<PixiExecTask>("npm_install_demoVanilla") {
        command=listOf("pnpm", "install", "--frozen-lockfile", "--ignore-workspace", "--config.node-linker=hoisted")
        workingDirectory.set(demoVanillaDir)
        doNotTrackState("pnpm already tracks its own state")
        dependsOn(buildMonaco)
    }

    register<PixiExecTask>("npm_run_demoVanilla") {
        command=listOf("pnpm", "run", "dev")
        workingDirectory.set(demoVanillaDir)
        dependsOn(installDemoVanilla)
    }

    val installDemoIframe = register<PixiExecTask>("npm_install_demoIframe") {
        command=listOf("pnpm", "install", "--frozen-lockfile", "--ignore-workspace", "--config.node-linker=hoisted")
        workingDirectory.set(demoIframeDir)
        doNotTrackState("pnpm already tracks its own state")
        dependsOn(buildMonaco)
        dependsOn(buildMonacoIframe)
    }

    register<PixiExecTask>("npm_run_demoIframe") {
        command=listOf("pnpm", "run", "dev")
        workingDirectory.set(demoIframeDir)
        dependsOn(installDemoIframe)
    }

    val installDemoReact = register<PixiExecTask>("npm_install_demoReact") {
        command=listOf("pnpm", "install", "--frozen-lockfile", "--ignore-workspace", "--config.node-linker=hoisted")
        workingDirectory.set(demoReactDir)
        doNotTrackState("pnpm already tracks its own state")
        dependsOn(buildMonaco)
    }

    register<PixiExecTask>("npm_run_demoReact") {
        command=listOf("pnpm", "run", "dev")
        workingDirectory.set(demoReactDir)
        dependsOn(installDemoReact)
    }

    // Single Playwright suite covering all three external-consumer demos.
    val demosDir = layout.projectDirectory.dir("demos")

    val installTestDemos = register<PixiExecTask>("npm_install_testDemos") {
        command=listOf("pnpm", "install", "--frozen-lockfile", "--ignore-workspace", "--config.node-linker=hoisted")
        workingDirectory.set(demosDir)
        doNotTrackState("pnpm already tracks its own state")
        dependsOn(buildMonaco)
        dependsOn(buildMonacoIframe)
        dependsOn(installDemoVanilla)
        dependsOn(installDemoIframe)
        dependsOn(installDemoReact)
    }

    val playwrightInstallTestDemos = register<PixiExecTask>("playwrightInstall_testDemos") {
        command=listOf("pnpm", "exec", "playwright", "install", "chromium")
        workingDirectory.set(demosDir)
        doNotTrackState("playwright already tracks its own state")
        dependsOn(installTestDemos)
    }

    val testDemos = register<PixiExecTask>("npm_run_testDemos") {
        command=listOf("pnpm", "test")
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
