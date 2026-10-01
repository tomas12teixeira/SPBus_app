@echo off
setlocal

set "GIT_BASH=C:\Program Files\Git\bin\bash.exe"
if exist "%GIT_BASH%" (
  "%GIT_BASH%" "%~dp0gradlew" %*
  exit /b %ERRORLEVEL%
)

where bash >nul 2>nul
if not errorlevel 1 (
  bash "%~dp0gradlew" %*
  exit /b %ERRORLEVEL%
)

echo "Git Bash nao foi encontrado. Instale Git para Windows para usar o Gradle wrapper."
exit /b 1
