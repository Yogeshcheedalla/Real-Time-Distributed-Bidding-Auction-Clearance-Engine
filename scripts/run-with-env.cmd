@echo off
rem Usage: scripts\run-with-env.cmd <maven args...>
setlocal
set "JAVA_HOME=C:\Users\LENOVO\AppData\Local\Akansha-jdk21\jdk-21.0.12.1+1"
set "PATH=%JAVA_HOME%\bin;%PATH%"
cd /d "%~dp0.."
if exist ".env" for /f "usebackq tokens=1,* delims==" %%A in (".env") do set "%%A=%%B"
mvn -B -ntp %*
endlocal
