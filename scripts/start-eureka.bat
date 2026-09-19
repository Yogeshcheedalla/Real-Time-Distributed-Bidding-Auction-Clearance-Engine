@echo off
call "%~dp0run-with-env.cmd" -pl services/eureka-server spring-boot:run
