@echo off

rem Simple uv wrapper for Windows that auto-installs uv locally if needed

set UV_DIR=%cd%\.uv
set UV_BIN=%UV_DIR%\uv.exe

rem Install uv locally if not present
if not exist "%UV_BIN%" (
    echo Installing uv locally to %UV_DIR%...

    rem Use uv's official installation script with custom install location
    set UV_UNMANAGED_INSTALL=%UV_DIR%

    rem Use pwsh if available, because legacy powershell causes problems when launched from pwsh!
    where pwsh >nul 2>&1
    if %errorlevel% equ 0 (
        pwsh -NoProfile -ExecutionPolicy ByPass -Command "iwr -useb https://astral.sh/uv/install.ps1 | iex"
    ) else (
        powershell -NoProfile -ExecutionPolicy ByPass -Command "iwr -useb https://astral.sh/uv/install.ps1 | iex"
    )

    if not exist "%UV_BIN%" (
        echo Failed to install uv
        exit /b 1
    )
)

rem Execute uv with all arguments
"%UV_BIN%" %*
