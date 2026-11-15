-- ========================================================
-- 手动初始化脚本（可选）
-- 应用启动时会自动执行 db/schema.sql 建表，一般不需要跑这个。
-- 只有在你想先手工建库、或想看清楚完整 DDL 时才用。
-- ========================================================

CREATE DATABASE IF NOT EXISTS iot_platform
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_general_ci;

USE iot_platform;

-- 表结构与 src/main/resources/db/schema.sql 完全一致
SOURCE ../src/main/resources/db/schema.sql;

-- 演示账号与设备由 DataInitializer 在应用首次启动时写入（密码用 BCrypt 生成，
-- 所以不放 SQL 里，避免手写 hash 出错）。
