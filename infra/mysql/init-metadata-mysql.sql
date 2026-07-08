CREATE DATABASE IF NOT EXISTS db_fleetops
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

GRANT ALL PRIVILEGES ON db_fleetops.* TO 'dbfleet'@'%';

FLUSH PRIVILEGES;