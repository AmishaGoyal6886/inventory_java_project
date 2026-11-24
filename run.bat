@echo off
setlocal
where mvn >nul 2>nul
if errorlevel 1 (
  echo Maven is not installed. Install Maven 3.8+ and Java 17+ first.
  echo Or set up Docker Desktop and run: docker compose up --build
  exit /b 1
)
if not exist .env (
  echo Missing .env file. Copy .env.example to .env and enter your MySQL connection details.
  echo The login and inventory features need the Java server and a reachable database.
  exit /b 1
)
for /f "usebackq tokens=1,* delims==" %%A in (".env") do if not "%%A"=="" if not "%%A:~0,1"=="#" set "%%A=%%B"
mvn -q package
if errorlevel 1 exit /b 1
start "Stockroom server" cmd /k java -jar target\stockroom-1.0.0.jar
set "ready="
for /l %%N in (1,1,30) do (
  powershell -NoProfile -Command "try { Invoke-WebRequest -Uri http://localhost:8080/api/health -TimeoutSec 1 | Out-Null; exit 0 } catch { exit 1 }" >nul 2>nul
  if not errorlevel 1 set "ready=1" & goto :open
  timeout /t 1 /nobreak >nul
)
echo Stockroom did not start. Check the Java server window and MySQL settings.
exit /b 1
:open
start "" http://localhost:8080
