@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo RustCraft: автоматическая проверка и загрузка свежего мода у хозяина.
echo Перед обновлением Minecraft должен быть закрыт.
echo Миры, настройки и сохранённый ник остаются на месте.
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0RustCraft.ps1" -InstallOnly
set "RC=%ERRORLEVEL%"
if not "%RC%"=="0" (
  echo Обновление не завершено. Прочитай сообщение об ошибке выше.
) else (
  echo Проверка завершена. Теперь запусти PLAY-RustCraft.bat.
)
pause
exit /b %RC%
