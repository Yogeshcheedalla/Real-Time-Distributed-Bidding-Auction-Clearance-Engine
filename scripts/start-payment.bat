@echo off
call "%~dp0run-with-env.cmd" -pl services/payment-service spring-boot:run
