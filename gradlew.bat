@echo off
setlocal
set PROJECT_DIR=%~dp0
set SRC_DIR=%PROJECT_DIR%src\main\java
set BUILD_DIR=%PROJECT_DIR%build
set CLASSES_DIR=%BUILD_DIR%\classes\java\main
set MAIN_CLASS=com.pinnacle.mcstats.MinecraftServerStatsApp
set JAR_NAME=mc-server-stats-manager-0.1.0-alpha.jar
set TASK=%1
if "%TASK%"=="" set TASK=run

if "%TASK%"=="clean" (
  if exist "%BUILD_DIR%" rmdir /s /q "%BUILD_DIR%"
  exit /b 0
)

mkdir "%CLASSES_DIR%" 2>nul
dir /s /b "%SRC_DIR%\*.java" > "%BUILD_DIR%\sources.txt"
javac --release 17 -d "%CLASSES_DIR%" @"%BUILD_DIR%\sources.txt"
if errorlevel 1 exit /b 1

if "%TASK%"=="run" (
  java -cp "%CLASSES_DIR%" %MAIN_CLASS%
  exit /b %errorlevel%
)
if "%TASK%"=="jar" goto jar
if "%TASK%"=="build" goto jar
if "%TASK%"=="assemble" goto jar

echo Supported tasks: run, jar, build, assemble, clean
exit /b 1

:jar
mkdir "%BUILD_DIR%\libs" 2>nul
cd /d "%CLASSES_DIR%"
jar --create --file "%BUILD_DIR%\libs\%JAR_NAME%" --main-class "%MAIN_CLASS%" .
echo Built %BUILD_DIR%\libs\%JAR_NAME%
