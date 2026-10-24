@echo off
:: ============================================================
::  run.bat  -  Build and Run on Windows (XAMPP + MySQL)
::  Requirements:
::    1. XAMPP installed, MySQL service STARTED in XAMPP Control Panel
::    2. mysql-connector-j-8.x.x.jar placed in this folder
::       (rename it to mysql-connector.jar)
::    3. Java JDK 17+ installed and on the PATH
::  Database:
::    - blood_bank.sql  (schema, functions, triggers, views, procedures)
::    - App auto-creates DB + procedures on first run
::    - Optional: import blood_bank.sql in phpMyAdmin for sample rows
::  PDF receipts:
::    - After donor registration or blood request, choose "Save PDF"
::    - Files are saved in the receipts\ folder (printable hard copy)
:: ============================================================

echo ==============================================
echo  Blood Bank Management System - Windows Run
echo ==============================================

:: Check java is available
java -version >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Java not found. Install JDK 17+ and add it to PATH.
    pause
    exit /b 1
)

:: Check the MySQL connector jar is present
if not exist mysql-connector.jar (
    echo ERROR: mysql-connector.jar not found in this folder.
    echo.
    echo Download MySQL Connector/J from:
    echo   https://dev.mysql.com/downloads/connector/j/
    echo Choose "Platform Independent" zip, extract the .jar file,
    echo rename it to mysql-connector.jar and place it here.
    pause
    exit /b 1
)

echo [1/2] Compiling all .java files...
javac -cp ".;mysql-connector.jar" *.java
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo Compilation FAILED. Fix the errors shown above.
    pause
    exit /b 1
)

echo [2/2] Launching the application...
echo Make sure MySQL is running in XAMPP Control Panel!
echo.
java -cp ".;mysql-connector.jar" Main
pause
