@echo off
chcp 65001 >nul
setlocal DisableDelayedExpansion
title 校园二手交易平台 - 前端
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\启动前端.ps1"
set "APP_EXIT_CODE=%ERRORLEVEL%"
echo.
echo 前端程序已结束。若启动失败，请查看上方错误信息。
pause
exit /b %APP_EXIT_CODE%
