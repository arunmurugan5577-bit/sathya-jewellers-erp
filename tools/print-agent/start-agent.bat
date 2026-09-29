@echo off
REM  Sathya Jewellers - shop print agent
REM  Leave this window open while the shop is trading.
cd /d "%~dp0"

where java >nul 2>&1
if errorlevel 1 (
  echo Java is not installed, or not on the PATH.
  echo Install a Java runtime 17 or newer, then run this again.
  pause
  exit /b 1
)

if not exist agent.properties (
  echo agent.properties is missing.
  echo Copy agent.properties.example to agent.properties and fill it in.
  pause
  exit /b 1
)

:run
java PrintAgent.java agent.properties
echo.
echo The agent stopped. Restarting in 10 seconds - close this window to stop.
timeout /t 10 /nobreak >nul
goto run
