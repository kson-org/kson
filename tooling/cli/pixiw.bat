@echo off

rem Simple Pixi wrapper for Windows that auto-installs Pixi locally if needed

set PIXI_DIR=%cd%\.pixi
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
