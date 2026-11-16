@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo Zapusk servera RustCraft. Java 8 dolzhna byt v JAVA_HOME ili PATH.
echo Mir hranitsya v papke server. Dlya ostanovki vvedite stop.
echo Podklyuchite Radmin VPN i razreshite TCP port 25576 v brandmauere.
call gradlew.bat runServer -PradminServer --console=plain
pause
