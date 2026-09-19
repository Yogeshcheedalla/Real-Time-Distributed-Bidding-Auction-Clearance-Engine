@echo off
call "%~dp0run-with-env.cmd" -pl services/auth-service spring-boot:run
