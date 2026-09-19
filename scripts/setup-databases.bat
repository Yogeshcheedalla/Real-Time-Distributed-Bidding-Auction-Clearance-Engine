@echo off
rem Creates the four service-owned databases on the local PostgreSQL server.
rem Prompts for the postgres password — it is never stored in this file or in git.
set "PGPASSWORD="
set /p PGPASSWORD=PostgreSQL superuser password (local): 
set "PSQL=C:\Program Files\PostgreSQL\18\bin\psql.exe"
if not exist "%PSQL%" set "PSQL=C:\Program Files\PostgreSQL\17\bin\psql.exe"
for %%D in (bidvelocity_auth bidvelocity_auction bidvelocity_bidding bidvelocity_payment) do (
  "%PSQL%" -h localhost -p 5432 -U postgres -tAc "SELECT 1 FROM pg_database WHERE datname='%%D'" | findstr /C:1 >nul || (
    echo creating %%D
    "%PSQL%" -h localhost -p 5432 -U postgres -c "CREATE DATABASE %%D"
  )
)
echo.
"%PSQL%" -h localhost -p 5432 -U postgres -tAc "SELECT datname FROM pg_database WHERE datname LIKE 'bidvelocity%%' ORDER BY 1"
set "PGPASSWORD="
