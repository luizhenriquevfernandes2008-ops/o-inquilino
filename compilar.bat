@echo off
rem Compila o mod "O Inquilino". Precisa de Java 25.
rem Se o JAVA_HOME nao apontar para um Java 25, usa o Java que vem com o launcher do Minecraft.
setlocal
set "MCJAVA=%LOCALAPPDATA%\Packages\Microsoft.4297127D64EC6_8wekyb3d8bbwe\LocalCache\Local\runtime\java-runtime-epsilon\windows-x64\java-runtime-epsilon"
if exist "%MCJAVA%\bin\javac.exe" set "JAVA_HOME=%MCJAVA%"
call "%~dp0gradlew.bat" build
if errorlevel 1 (
  echo.
  echo Falhou. Instale o Java 25 ^(https://adoptium.net^) e tente de novo.
  pause
  exit /b 1
)
echo.
echo Pronto! O mod esta em build\libs\o-inquilino-1.1.0.jar
pause
