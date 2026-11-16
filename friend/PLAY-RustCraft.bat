@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo RustCraft: автоматическая проверка обновлений у хозяина перед запуском.
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0RustCraft.ps1"
set "RC=%ERRORLEVEL%"
if not "%RC%"=="0" echo Запуск завершился с ошибкой. Прочитай сообщение выше.
pause
exit /b %RC%
