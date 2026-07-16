@echo off
setlocal EnableExtensions
rem ISPF all-in-one launcher (Windows). Requires JDK 25+ on PATH.
rem Place this file next to ispf-server.jar, or pass the JAR path as %%1.

cd /d "%~dp0"
if not "%~1"=="" (
  set "JAR=%~1"
) else if exist "ispf-server.jar" (
  set "JAR=ispf-server.jar"
) else if exist "..\ispf-server.jar" (
  set "JAR=..\ispf-server.jar"
) else (
  echo Missing ispf-server.jar
  echo Usage: start.bat [path\to\ispf-server.jar]
  echo Put start.bat next to the JAR, or pass the path.
  pause
  exit /b 1
)

where java >nul 2>nul
if errorlevel 1 (
  echo Java not found on PATH. Install JDK 25+ and try again.
  pause
  exit /b 1
)

if not defined ISPF_DATA_DIR set "ISPF_DATA_DIR=%cd%\data"
if not exist "%ISPF_DATA_DIR%" mkdir "%ISPF_DATA_DIR%"

echo Starting ISPF ^(local profile, H2^) — open http://localhost:8080
echo Default login: admin / admin
echo.
java -jar "%JAR%" --spring.profiles.active=local
set "EXIT_CODE=%ERRORLEVEL%"
if not "%EXIT_CODE%"=="0" (
  echo.
  echo ISPF exited with code %EXIT_CODE%
  pause
)
exit /b %EXIT_CODE%
