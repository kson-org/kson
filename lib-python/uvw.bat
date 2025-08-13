@if "%DEBUG%"=="" @echo off
@rem ##########################################################################
@rem
@rem  UV startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
@rem This is normally unused
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

@rem Resolve any "." and ".." in APP_HOME to make it shorter.
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

@rem UV WRAPPER START MARKER

setlocal
set BUILD_DIR=%APP_HOME%.uv
set UV_VERSION=0.8.9
set UV_TARGET_DIR=%BUILD_DIR%\%UV_VERSION%

set UV_URL=https://github.com/astral-sh/uv/releases/download/%UV_VERSION%/uv-x86_64-pc-windows-msvc.zip
set UV_TEMP_FILE=uv.zip

set POWERSHELL=%SystemRoot%\system32\WindowsPowerShell\v1.0\powershell.exe

if not exist "%BUILD_DIR%" MD "%BUILD_DIR%"

if not exist "%UV_TARGET_DIR%" goto downloadAndExtractUv

if exist "%UV_TARGET_DIR%\uv.exe" goto continueWithUv

:downloadAndExtractUv

PUSHD "%BUILD_DIR%"
if errorlevel 1 goto fail

echo Downloading %UV_URL% to %BUILD_DIR%\%UV_TEMP_FILE%
if exist "%UV_TEMP_FILE%" DEL /F "%UV_TEMP_FILE%"
"%POWERSHELL%" -nologo -noprofile -Command "Set-StrictMode -Version 3.0; $ErrorActionPreference = \"Stop\"; try { (New-Object Net.WebClient).DownloadFile('%UV_URL%', '%UV_TEMP_FILE%') } catch { Write-Host \"Failed to download UV: $_\"; exit 1 }"
if errorlevel 1 goto fail

POPD

if exist "%UV_TARGET_DIR%" RMDIR /S /Q "%UV_TARGET_DIR%"
if errorlevel 1 goto fail

MKDIR "%UV_TARGET_DIR%"
if errorlevel 1 goto fail

PUSHD "%UV_TARGET_DIR%"
if errorlevel 1 goto fail

echo Extracting %BUILD_DIR%\%UV_TEMP_FILE% to %UV_TARGET_DIR%

"%POWERSHELL%" -nologo -noprofile -command "Set-StrictMode -Version 3.0; $ErrorActionPreference = \"Stop\"; Add-Type -A 'System.IO.Compression.FileSystem'; [IO.Compression.ZipFile]::ExtractToDirectory('..\\%UV_TEMP_FILE%', '.');"
if errorlevel 1 goto fail

DEL /F "..\%UV_TEMP_FILE%"
if errorlevel 1 goto fail

POPD

:continueWithUv

set UV_EXE=%UV_TARGET_DIR%\uv.exe

endlocal & set UV_EXE=%UV_EXE%

@rem UV WRAPPER END MARKER

@rem Check if uv.exe exists
if not exist "%UV_EXE%" (
  echo. 1>&2
  echo ERROR: UV executable not found at %UV_EXE% 1>&2
  echo. 1>&2
  echo Please check the installation or try deleting the .uv directory to force re-download. 1>&2
  goto fail
)

:execute
@rem Execute UV with all passed arguments
"%UV_EXE%" %*

:end
@rem End local scope for the variables with windows NT shell
if %ERRORLEVEL% equ 0 goto mainEnd

:fail
rem Set variable UV_EXIT_CONSOLE if you need the _script_ return code instead of
rem the _cmd.exe /c_ return code!
set EXIT_CODE=%ERRORLEVEL%
if %EXIT_CODE% equ 0 set EXIT_CODE=1
if not ""=="%UV_EXIT_CONSOLE%" exit %EXIT_CODE%
exit /b %EXIT_CODE%

:mainEnd
if "%OS%"=="Windows_NT" endlocal

:omega
