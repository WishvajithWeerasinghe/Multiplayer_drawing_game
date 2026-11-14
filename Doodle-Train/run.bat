@echo off
cd /d "%~dp0"
if not exist out mkdir out
javac -encoding UTF-8 -d out server\GameServer.java
if errorlevel 1 (
  pause
  exit /b 1
)
java -cp out GameServer
pause
