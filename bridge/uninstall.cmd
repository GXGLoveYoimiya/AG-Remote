@echo off
rem Double-click to uninstall the AG Remote PC service (logic is in uninstall.ps1). ASCII only.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0uninstall.ps1" %*
pause
