@echo off
echo ========================================
echo 数据库迁移：添加 phone 字段（安全版本）
echo ========================================
echo.
echo 此脚本会检查字段和索引是否存在，避免重复添加
echo.

set MYSQL_USER=root
set MYSQL_PASSWORD=123456
set MYSQL_HOST=localhost
set MYSQL_PORT=3306
set MYSQL_DB=travel_app

echo 正在执行数据库迁移...
mysql -u%MYSQL_USER% -p%MYSQL_PASSWORD% -h%MYSQL_HOST% -P%MYSQL_PORT% %MYSQL_DB% < migrate_add_phone_safe.sql

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo 数据库迁移成功完成!
    echo ========================================
) else (
    echo.
    echo ========================================
    echo 数据库迁移失败，请检查错误信息
    echo ========================================
)

echo.
pause
