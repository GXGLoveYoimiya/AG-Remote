@echo off
rem AG Remote service watchdog: restarts server.mjs 3 s after it exits. Started hidden by start-service.vbs.
rem Keep this file ASCII-only: cmd parses batch files in the OEM code page, and it re-reads
rem the file while the loop runs, so do not edit it while the service is running.
cd /d "%~dp0"
if not exist data mkdir data
rem Node: env var NODE, then node.exe next to this script (installed package),
rem then _toolchain\node\node.exe two levels above the repo (dev checkout), then node on PATH
if not defined NODE if exist "%~dp0node.exe" set "NODE=%~dp0node.exe"
if not defined NODE if exist "..\..\..\_toolchain\node\node.exe" set "NODE=..\..\..\_toolchain\node\node.exe"
if not defined NODE set "NODE=node"
rem One watchdog per folder: holding data\watchdog.lock open is the lock; a second copy cannot open it and quits.
(call :loop) 9>"data\watchdog.lock" 2>nul
exit /b %errorlevel%

:loop
if exist data\server.log copy /y data\server.log data\server.prev.log >nul 2>&1
"%NODE%" server.mjs < nul > data\server.log 2>&1
rem Exit code 3 = port already in use (another AG Remote is running): stop instead of retrying forever
if %errorlevel%==3 exit /b 3
rem 3 s pause; ping works even without a console (timeout does not)
ping -n 4 127.0.0.1 >nul
goto loop
