SET @column_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'users'
      AND COLUMN_NAME = 'cover_image'
);

SET @ddl := IF(
    @column_exists = 0,
    'ALTER TABLE users ADD COLUMN cover_image VARCHAR(500) DEFAULT NULL COMMENT ''cover image URL''',
    'SELECT 1'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
