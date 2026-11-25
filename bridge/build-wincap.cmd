@echo off
rem Build wincap.exe (screen capture + mouse/keyboard helper) with the .NET Framework 4 compiler built into Windows.
rem Usage: build-wincap.cmd [output path]   (default: wincap.exe next to this script)
rem Keep this file ASCII-only: cmd parses batch files in the OEM code page.
setlocal
cd /d "%~dp0"
set "OUT=%~1"
if "%OUT%"=="" set "OUT=wincap.exe"
set "CSC=%WINDIR%\Microsoft.NET\Framework64\v4.0.30319\csc.exe"
if not exist "%CSC%" set "CSC=%WINDIR%\Microsoft.NET\Framework\v4.0.30319\csc.exe"
if not exist "%CSC%" (
  echo csc.exe not found. Enable .NET Framework 4.8 in "Turn Windows features on or off".
  exit /b 1
)
"%CSC%" /nologo /optimize+ /target:exe /r:System.Drawing.dll /out:"%OUT%" wincap.cs
if errorlevel 1 (
  echo Build failed. If the service is running, wincap.exe is locked: run uninstall.cmd first.
  exit /b 1
)
echo Built %OUT%
