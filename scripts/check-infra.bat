@echo off
echo [check] PostgreSQL :5432
powershell -NoProfile -Command "if (Test-NetConnection localhost -Port 5432 -InformationLevel Quiet -WarningAction SilentlyContinue) { '  PostgreSQL: UP' } else { '  PostgreSQL: DOWN — start the postgresql-x64 service' }"
echo [check] Redis :6379 (optional)
powershell -NoProfile -Command "if (Test-NetConnection localhost -Port 6379 -InformationLevel Quiet -WarningAction SilentlyContinue) { '  Redis: UP' } else { '  Redis: not running (optional — see docs\infrastructure.md)' }"
echo [check] Kafka :9092 (optional)
powershell -NoProfile -Command "if (Test-NetConnection localhost -Port 9092 -InformationLevel Quiet -WarningAction SilentlyContinue) { '  Kafka: UP' } else { '  Kafka: not running (optional — services fall back to in-process events)' }"
echo [check] Eureka :8761
powershell -NoProfile -Command "if (Test-NetConnection localhost -Port 8761 -InformationLevel Quiet -WarningAction SilentlyContinue) { '  Eureka: UP' } else { '  Eureka: DOWN — run scripts\start-eureka.bat' }"
