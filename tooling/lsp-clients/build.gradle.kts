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

    register<PixiExecTask>("npm_run_monaco") {
        command=listOf("npm", "run", "monaco")
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
            val bundle = File(distDir, "kson-monaco.js")
            if (bundle.exists()) {
                println()
                println("=".repeat(60))
                println("@kson/monaco-editor built successfully!")
                println("  Output: ${distDir.absolutePath}")
                println()
                println("Dev server (with demo):")
                println("  ./gradlew tooling:lsp-clients:npm_run_monaco")
                println()
                println("Usage:")
                println("""
                    |  import { createKsonEditor } from '@kson/monaco-editor';
                    |
                    |  await createKsonEditor(container, {
                    |      lspOptions: {
                    |          bundledSchemas: [{
                    |              fileExtension: 'myformat.kson',
                    |              schemaContent: myJsonSchemaString,
                    |          }],
                    |          enableBundledSchemas: true,
                    |      },
                    |  });
                """.trimMargin())
                println()
                println("Adding schemas:")
                println("  Each entry in bundledSchemas maps a file extension to a")
                println("  JSON Schema (draft-07) string. Documents whose URI ends")
                println("  in .{fileExtension} get that schema's validation,")
                println("  completions, and hover. The most specific extension wins")
                println("  (e.g. 'orchestra.kson' beats 'kson' for a .orchestra.kson file).")
                println("=".repeat(60))
            }
        }
    }

    register<PixiExecTask>("npm_run_buildMonacoIframe") {
        command=listOf("npm", "run", "buildMonacoIframe")
        dependsOn(npmInstall)
        doLast {
            val iframeDir = file("monaco/dist-iframe")
            val editorHtml = File(iframeDir, "kson-editor.html")
            if (editorHtml.exists()) {
                println()
                println("=".repeat(60))
                println("KSON Monaco iframe built successfully!")
                println("  Output: ${iframeDir.absolutePath}")
                println()
                println("Copy dist-iframe/ to your static assets, then:")
                println("""
                    |  <script src="kson-editor.js"></script>
                    |  <script>
                    |    const editor = await KsonEditor.create(
                    |      document.getElementById('editor'),
                    |      {
                    |        value: '{ name: "hello" }',
                    |        schema: {
                    |          fileExtension: 'kson',
                    |          schemaContent: myJsonSchemaString,
                    |        },
                    |        onChange(value) { console.log('changed:', value); },
                    |      },
                    |    );
                    |
                    |    editor.getValue();    // synchronous
                    |    editor.setValue('{}'); // update content
                    |    editor.dispose();     // clean up
                    |  </script>
                """.trimMargin())
                println("=".repeat(60))
            }
        }
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
        delete("monaco/dist-iframe")
        delete("shared/out")
    }
}
