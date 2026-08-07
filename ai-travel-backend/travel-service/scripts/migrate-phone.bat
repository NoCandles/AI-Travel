@echo off
echo ========================================
echo 数据库迁移：添加 phone 字段（简单版本）
echo ========================================
echo.
echo 注意：如果字段已存在，会报错，请使用 migrate-phone-safe.bat
echo.

set MYSQL_USER=root
set MYSQL_PASSWORD=123456
set MYSQL_HOST=localhost
set MYSQL_PORT=3306
set MYSQL_DB=travel_app

echo 正在执行数据库迁移...
mysql -u%MYSQL_USER% -p%MYSQL_PASSWORD% -h%MYSQL_HOST% -P%MYSQL_PORT% %MYSQL_DB% < migrate_add_phone.sql

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo 数据库迁移成功完成!
    echo ========================================
) else (
    echo.
    echo ========================================
    echo 数据库迁移失败，可能字段已存在
    echo 请使用：migrate-phone-safe.bat
    echo ========================================
)

echo.
pause
