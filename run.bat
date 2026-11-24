@echo off
setlocal
where mvn >nul 2>nul
if errorlevel 1 (
  echo Maven is not installed. Opening the browser preview so you can use the website now.
  echo To enable Java and MySQL, install Maven, configure .env, and run this file again.
  start "" "%~dp0index.html"
  exit /b 0
)
if not exist .env (
  echo Missing .env file. Copy .env.example to .env and enter your MySQL password.
  echo Opening the browser preview while you configure the database.
  start "" "%~dp0index.html"
  exit /b 0
)
for /f "usebackq tokens=1,* delims==" %%A in (".env") do if not "%%A"=="" if not "%%A:~0,1"=="#" set "%%A=%%B"
mvn -q package
if errorlevel 1 exit /b 1
start "Stockroom server" cmd /k java -jar target\stockroom-1.0.0.jar
timeout /t 3 /nobreak >nul
start "" http://localhost:8080
