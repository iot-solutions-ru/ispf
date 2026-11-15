@echo off
setlocal EnableExtensions
rem ISPF all-in-one launcher (Windows). Requires JDK 25+ on PATH.
rem Place this file next to ispf-server.jar, or pass the JAR path as %%1.
rem Database: embedded H2 file (no PostgreSQL). File: data\ispf-local.mv.db

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

rem Relative ./data avoids Windows backslash issues in the H2 JDBC URL.
if not defined ISPF_DATA_DIR set "ISPF_DATA_DIR=./data"
rem If a custom absolute path was set, normalize \ to / for H2.
set "ISPF_DATA_DIR=%ISPF_DATA_DIR:\=/%"
if not exist "%ISPF_DATA_DIR%" mkdir "%ISPF_DATA_DIR%"

rem Portable zip ships the evaluation set in .\drivers (not under data\, so a DB reset keeps it).
rem drivers\* matches a non-empty directory, not a file named drivers.
rem If this launcher sits in scripts\ and the JAR is next to drivers\, use that folder.
if not defined ISPF_DRIVER_PACKS_DIR if exist "drivers\*" set "ISPF_DRIVER_PACKS_DIR=%CD%\drivers"
if not defined ISPF_DRIVER_PACKS_DIR for %%I in ("%JAR%") do set "JAR_DIR=%%~dpI"
if not defined ISPF_DRIVER_PACKS_DIR if exist "%JAR_DIR%drivers\*" set "ISPF_DRIVER_PACKS_DIR=%JAR_DIR%drivers"

echo Starting ISPF ^(local profile^)
echo   UI:       http://localhost:8080
echo   Login:    admin / admin
echo   Database: embedded H2 — %CD%\data\ispf-local.mv.db
echo             ^(no separate PostgreSQL required^)
if defined ISPF_DRIVER_PACKS_DIR echo   Drivers:  %ISPF_DRIVER_PACKS_DIR%
echo.
java -jar "%JAR%" --spring.profiles.active=local
set "EXIT_CODE=%ERRORLEVEL%"
if not "%EXIT_CODE%"=="0" (
  echo.
  echo ISPF exited with code %EXIT_CODE%
  pause
)
exit /b %EXIT_CODE%
