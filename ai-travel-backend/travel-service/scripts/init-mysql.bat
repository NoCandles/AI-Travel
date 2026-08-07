@echo off
chcp 65001 >nul
echo ================================
echo 初始化 MySQL 数据库
echo ================================

set MYSQL_BIN="C:\Program Files\MySQL\MySQL Server 5.7\bin\mysql.exe"
set MYSQL_USER=root
set MYSQL_PASS=admin

echo.
echo 正在创建数据库 travel_app...
%MYSQL_BIN% -u%MYSQL_USER% -p%MYSQL_PASS% -e "CREATE DATABASE IF NOT EXISTS travel_app DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

if %ERRORLEVEL% EQU 0 (
    echo ✓ 数据库创建成功
) else (
    echo ✗ 数据库创建失败，请检查 MySQL 服务
    pause
    exit /b 1
)

echo.
echo 正在创建表结构...
%MYSQL_BIN% -u%MYSQL_USER% -p%MYSQL_PASS% travel_app < init-db.sql

if %ERRORLEVEL% EQU 0 (
    echo ✓ 表结构创建成功
) else (
    echo ✗ 表结构创建失败
    pause
    exit /b 1
)

echo.
echo ================================
echo 数据库初始化完成！
echo ================================
pause
