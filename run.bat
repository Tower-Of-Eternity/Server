@echo off
chcp 65001 > nul
cd /d "%~dp0"
title Tower of Eternity - Game Server

echo ====================================================
echo        STARTING TOWER OF ETERNITY GAME SERVER
echo ====================================================
echo.

java -jar target\server-0.0.1-SNAPSHOT.jar 2>nul
if errorlevel 1 (
    echo [INFO] Chua co file JAR, tien hanh compile va chay truc tiep...
    mvn spring-boot:run
)

pause
