@echo off
rem Double-click to install the AG Remote PC service (logic is in install.ps1). ASCII only.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0install.ps1"
pause
