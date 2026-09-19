@echo off
call "%~dp0run-with-env.cmd" -pl services/api-gateway spring-boot:run
