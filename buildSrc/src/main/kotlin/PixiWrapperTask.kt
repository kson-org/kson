import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class PixiWrapperTask : DefaultTask() {
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Input
    @get:Optional
    abstract val pixiInstallDir: Property<String>

    init {
        description = "Generates Pixi wrapper scripts (pixiw and pixiw.bat)"
        group = "pixi"
    }

    @TaskAction
    fun generateWrappers() {
        val dir = outputDir.get().asFile
        val installDir = pixiInstallDir.get()

        generateUnixWrapper(dir, installDir)
        generateWindowsWrapper(dir, installDir)

        logger.lifecycle("Generated Pixi wrapper scripts in ${dir.absolutePath}")
    }

    private fun generateUnixWrapper(dir: File, installDir: String) {
        val wrapper = File(dir, "pixiw")
        wrapper.writeText("""
#!/bin/sh

# Simple Pixi wrapper that auto-installs Pixi locally if needed

PIXI_DIR="${'$'}(pwd)/$installDir"
PIXI_BIN="${'$'}PIXI_DIR/bin/pixi"

# Install Pixi locally if not present
if [ ! -f "${'$'}PIXI_BIN" ]; then
    echo "Installing Pixi locally to ${'$'}PIXI_DIR..."

    # Use Pixi's official installation script with custom install location
    export PIXI_HOME="${'$'}PIXI_DIR"
    export PIXI_NO_PATH_UPDATE=1

    if command -v curl >/dev/null 2>&1; then
        curl -fsSL https://pixi.sh/install.sh | bash
    elif command -v wget >/dev/null 2>&1; then
        wget -qO- https://pixi.sh/install.sh | bash
    else
        echo "Please install curl or wget" >&2
        exit 1
    fi

    if [ ! -f "${'$'}PIXI_BIN" ]; then
        echo "Failed to install Pixi" >&2
        exit 1
    fi
fi

# Execute Pixi with all arguments
exec "${'$'}PIXI_BIN" "${'$'}@"
        """.trimIndent())

        wrapper.setExecutable(true)
    }

    private fun generateWindowsWrapper(dir: File, installDir: String) {
        val wrapper = File(dir, "pixiw.bat")
        val installDirWindows = installDir.replace("/", "\\")
        
        wrapper.writeText("""
@echo off

rem Simple Pixi wrapper for Windows that auto-installs Pixi locally if needed

set PIXI_DIR=%cd%\$installDirWindows
set PIXI_BIN=%PIXI_DIR%\bin\pixi.exe

rem Install Pixi locally if not present
if not exist "%PIXI_BIN%" (
    echo Installing Pixi locally to %PIXI_DIR%...

    rem Use Pixi's official installation script with custom install location
    set PIXI_HOME=%PIXI_DIR%
    set PIXI_NO_PATH_UPDATE=1

    powershell -ExecutionPolicy ByPass -Command "iwr -useb https://pixi.sh/install.ps1 | iex"

    if not exist "%PIXI_BIN%" (
        echo Failed to install Pixi
        exit /b 1
    )
)

rem Execute Pixi with all arguments
"%PIXI_BIN%" %*
        """.trimIndent())
    }
}