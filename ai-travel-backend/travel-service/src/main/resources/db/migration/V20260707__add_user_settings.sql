SET @column_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'users'
      AND COLUMN_NAME = 'settings'
);

SET @ddl := IF(
    @column_exists = 0,
    'ALTER TABLE users ADD COLUMN settings TEXT DEFAULT NULL COMMENT ''user system settings JSON''',
    'SELECT 1'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
