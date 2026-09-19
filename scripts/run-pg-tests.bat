@echo off
setlocal enabledelayedexpansion
rem Runs the bidding concurrency suite against the REAL local PostgreSQL
rem (isolated temp schemas, production Flyway SQL). Reads credentials from .env —
rem they never appear in git or in this file.
cd /d "%~dp0.."
for /f "usebackq tokens=1,* delims==" %%A in (".env") do set "BV_%%A=%%B"
if "!BV_DB_PASSWORD!"=="change-me-locally" (
  echo .env still has the placeholder DB_PASSWORD — edit .env first ^(not this chat^). & exit /b 1
)
set "BV_PG_URL=jdbc:postgresql://!BV_DB_HOST!:!BV_DB_PORT!/postgres"
set "BV_PG_USER=!BV_DB_USERNAME!"
set "BV_PG_PASSWORD=!BV_DB_PASSWORD!"
set "JAVA_HOME=C:\Users\LENOVO\AppData\Local\Akansha-jdk21\jdk-21.0.12.1+1"
set "PATH=%JAVA_HOME%\bin;%PATH%"
call mvn -B -ntp -pl services/bidding-service -Dtest=BidPostgresConcurrencyIT test
set RC=!errorlevel!
if not "!RC!"=="0" if not "!RC!"=="-1" (
  echo.
  echo NOTE: if connectivity failed, verify service postgresql-x64 is running.
)
exit /b !RC!
