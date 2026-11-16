@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo RustCraft: подготовка и установка файлов без запуска игры.
echo Готовые файлы проверяются, недостающие скачиваются автоматически.
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0RustCraft.ps1" -InstallOnly
set "RC=%ERRORLEVEL%"
if not "%RC%"=="0" (
  echo Установка не завершена. Прочитай сообщение об ошибке выше.
) else (
  echo Готово. Для игры запусти PLAY-RustCraft.bat.
)
pause
exit /b %RC%
