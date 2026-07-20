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

        logger.info("Generated Pixi wrapper scripts in ${dir.absolutePath}")
    }

    private fun generateUnixWrapper(dir: File, installDir: String) {
        val wrapper = File(dir, "pixiw")
        wrapper.writeText("""
#!/bin/sh

# Simple Pixi wrapper that auto-installs Pixi locally if needed

PIXI_DIR="${'$'}(pwd)/$installDir"
PIXI_BIN="${'$'}PIXI_DIR/bin/pixi"
PINNED_PIXI_VERSION="$PINNED_PIXI_VERSION"

# Install Pixi locally if missing or off the pin: the install script overwrites in place
if [ ! -f "${'$'}PIXI_BIN" ] || [ "${'$'}("${'$'}PIXI_BIN" --version 2>/dev/null)" != "pixi ${'$'}PINNED_PIXI_VERSION" ]; then
    echo "Installing Pixi ${'$'}PINNED_PIXI_VERSION locally to ${'$'}PIXI_DIR..."

    # Use Pixi's official installation script with custom install location
    export PIXI_HOME="${'$'}PIXI_DIR"
    export PIXI_NO_PATH_UPDATE=1
    export PIXI_VERSION="v${'$'}PINNED_PIXI_VERSION"

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

        // Written with CRLF: cmd.exe wants it, and the root .gitattributes checks `.bat` files out that way
        wrapper.writeText(crlf("""
@echo off

rem Simple Pixi wrapper for Windows that auto-installs Pixi locally if needed

set PIXI_DIR=%cd%\$installDirWindows
set PIXI_BIN=%PIXI_DIR%\bin\pixi.exe
set PINNED_PIXI_VERSION=$PINNED_PIXI_VERSION

rem Install Pixi locally if missing or off the pin: the install script overwrites in place
if not exist "%PIXI_BIN%" goto install
"%PIXI_BIN%" --version 2>nul | findstr /x /c:"pixi %PINNED_PIXI_VERSION%" >nul
if %errorlevel% equ 0 goto run

:install
echo Installing Pixi %PINNED_PIXI_VERSION% locally to %PIXI_DIR%...

rem Use Pixi's official installation script with custom install location
set PIXI_HOME=%PIXI_DIR%
set PIXI_NO_PATH_UPDATE=1
set PIXI_VERSION=v%PINNED_PIXI_VERSION%

powershell -ExecutionPolicy ByPass -Command "iwr -useb https://pixi.sh/install.ps1 | iex"

if not exist "%PIXI_BIN%" (
    echo Failed to install Pixi
    exit /b 1
)

:run
rem Execute Pixi with all arguments
"%PIXI_BIN%" %*
        """.trimIndent()))
    }

    private fun crlf(text: String) = text.replace("\r\n", "\n").replace("\n", "\r\n")

    companion object {
        // Version of Pixi the generated wrappers pin their auto-install to, exactly as `pixi --version` reports it
        internal const val PINNED_PIXI_VERSION = "0.73.0"
    }
}