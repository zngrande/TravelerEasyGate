-- ============================================================
-- Traveler Easy Gate (Useful Travel) 資料庫「一次到位」腳本 (可重複執行)
-- ============================================================
-- 整合來源: db.zip 內 useful_travel_schema.sql + 全部 migration_*.sql + before_flyway_catchup.sql
--
-- 特性:
--   * 全部「已存在就跳過」: 資料表用 CREATE TABLE IF NOT EXISTS; 欄位 / 索引 / 外鍵 / 種子資料
--     都是先查 information_schema (或 NOT EXISTS) 才執行, 空資料庫、舊資料庫、已是最新的資料庫都能直接匯入,
--     重複匯入幾次都不會報錯, 也不會改動既有資料。
--   * 不依賴 MySQL 8.0.29 的 "ADD COLUMN IF NOT EXISTS" 語法, MySQL 5.7 / 8.x / MariaDB 都能跑。
--   * 不需要 DELIMITER, 用 phpMyAdmin / Workbench / DBeaver / mysql 指令列都可以整份匯入。
--
-- 使用方式: 先選好 (或 USE) 你要更新的資料庫, 再整份匯入。
--   mysql -u root -p traveler_easy_gate < traveler_easy_gate_full_idempotent.sql
--
-- 刻意「不包含」的項目 (會破壞或刪除你現有資料):
--   * migration_poi_airport_category.sql / migration_poi_category_normalize.sql
--       (把 poi.category 由中文改英文; 你的資料庫已整理成中文, 程式也已配合中文, 不能跑)
--   * migration_drop_poi_cost_and_cooperation.sql
--       (DROP poi.cost_price 與 poi_cooperation_log; 這是刪資料, 留在原處不動, 程式不讀它也無害)
--   * rename_database_useful_travel_to_traveler_easy_gate.sql (改資料庫名稱, 與結構無關)
-- ============================================================

SET NAMES utf8mb4;
CREATE DATABASE IF NOT EXISTS traveler_easy_gate DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE traveler_easy_gate;

SET NAMES utf8mb4;
SET SQL_SAFE_UPDATES = 0;


-- ============================================================
-- 一、資料表 (不存在才建立)
-- ============================================================
CREATE TABLE IF NOT EXISTS agency (
    AID INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    license_no VARCHAR(50),
    contact_phone VARCHAR(20),
    contact_email VARCHAR(100),
    plan_type VARCHAR(20) DEFAULT 'trial',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS staff_user (
    UID INT AUTO_INCREMENT PRIMARY KEY,
    AID INT NOT NULL,
    name VARCHAR(50) NOT NULL,
    phone VARCHAR(20),
    account VARCHAR(50) NOT NULL UNIQUE,
    pw VARCHAR(255) NOT NULL,
    role VARCHAR(60) DEFAULT 'VIEWER',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (AID) REFERENCES agency(AID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS poi (
    PID INT AUTO_INCREMENT PRIMARY KEY,
    AID INT DEFAULT NULL,
    category VARCHAR(20) NOT NULL,
    name VARCHAR(100) NOT NULL,
    country VARCHAR(50),
    city VARCHAR(50),
    address VARCHAR(255),
    latitude DECIMAL(10,7),
    longitude DECIMAL(10,7),
    suggested_stay_min INT DEFAULT 60,
    open_hours VARCHAR(255),
    description TEXT,
    travel_style_tags TEXT,
    partner_priority BOOLEAN NOT NULL DEFAULT FALSE,
    star_rating DECIMAL(2,1),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (AID) REFERENCES agency(AID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS poi_image (
    IID INT AUTO_INCREMENT PRIMARY KEY,
    PID INT NOT NULL,
    image_url VARCHAR(500) NOT NULL,
    sort_order INT DEFAULT 0,
    FOREIGN KEY (PID) REFERENCES poi(PID) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS itinerary (
    ITID INT AUTO_INCREMENT PRIMARY KEY,
    AID INT NOT NULL,
    created_by INT NOT NULL,
    title VARCHAR(100) NOT NULL,
    country VARCHAR(50),
    days_count INT NOT NULL DEFAULT 1,
    start_date DATE,
    end_date DATE,
    group_size INT,
    status VARCHAR(20) DEFAULT 'draft',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (AID) REFERENCES agency(AID),
    FOREIGN KEY (created_by) REFERENCES staff_user(UID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS itinerary_day (
    IDID INT AUTO_INCREMENT PRIMARY KEY,
    ITID INT NOT NULL,
    day_number INT NOT NULL,
    day_date DATE,
    theme VARCHAR(100),
    FOREIGN KEY (ITID) REFERENCES itinerary(ITID) ON DELETE CASCADE,
    UNIQUE KEY uk_day (ITID, day_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS itinerary_item (
    IIID INT AUTO_INCREMENT PRIMARY KEY,
    IDID INT NOT NULL,
    PID INT DEFAULT NULL,
    item_type VARCHAR(20) NOT NULL,
    custom_name VARCHAR(100),
    sort_order INT NOT NULL DEFAULT 0,
    start_time TIME,
    end_time TIME,
    stay_duration_min INT,
    note TEXT,
    FOREIGN KEY (IDID) REFERENCES itinerary_day(IDID) ON DELETE CASCADE,
    FOREIGN KEY (PID) REFERENCES poi(PID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS route_segment (
    RSID INT AUTO_INCREMENT PRIMARY KEY,
    IDID INT NOT NULL,
    from_item_id INT NOT NULL,
    to_item_id INT NOT NULL,
    distance_km DECIMAL(6,2),
    duration_min INT,
    transport_mode VARCHAR(20) DEFAULT 'driving',
    is_backtrack BOOLEAN DEFAULT FALSE,
    calculated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (IDID) REFERENCES itinerary_day(IDID) ON DELETE CASCADE,
    FOREIGN KEY (from_item_id) REFERENCES itinerary_item(IIID),
    FOREIGN KEY (to_item_id) REFERENCES itinerary_item(IIID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS component (
    CPID INT AUTO_INCREMENT PRIMARY KEY,
    AID INT NOT NULL,
    type VARCHAR(20) NOT NULL,
    name VARCHAR(100) NOT NULL,
    default_price DECIMAL(10,2),
    description TEXT,
    FOREIGN KEY (AID) REFERENCES agency(AID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS itinerary_component (
    ICID INT AUTO_INCREMENT PRIMARY KEY,
    ITID INT NOT NULL,
    CPID INT NOT NULL,
    day_number INT,
    quantity INT DEFAULT 1,
    price_override DECIMAL(10,2),
    FOREIGN KEY (ITID) REFERENCES itinerary(ITID) ON DELETE CASCADE,
    FOREIGN KEY (CPID) REFERENCES component(CPID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS export_history (
    EHID INT AUTO_INCREMENT PRIMARY KEY,
    ITID INT NOT NULL,
    format VARCHAR(10) NOT NULL,
    file_url VARCHAR(500),
    generated_by INT,
    generated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (ITID) REFERENCES itinerary(ITID) ON DELETE CASCADE,
    FOREIGN KEY (generated_by) REFERENCES staff_user(UID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_import (
    IPID INT AUTO_INCREMENT PRIMARY KEY,
    AID INT NOT NULL,
    created_by INT NOT NULL,
    source_type VARCHAR(20) NOT NULL DEFAULT 'text',
    raw_content MEDIUMTEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    error_message VARCHAR(500),
    result_itinerary_id INT DEFAULT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (AID) REFERENCES agency(AID),
    FOREIGN KEY (created_by) REFERENCES staff_user(UID),
    FOREIGN KEY (result_itinerary_id) REFERENCES itinerary(ITID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_parsed_day (
    APDID INT AUTO_INCREMENT PRIMARY KEY,
    IPID INT NOT NULL,
    day_number INT NOT NULL,
    theme VARCHAR(200),
    FOREIGN KEY (IPID) REFERENCES ai_import(IPID) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_parsed_item (
    APIID INT AUTO_INCREMENT PRIMARY KEY,
    APDID INT NOT NULL,
    item_type VARCHAR(20) NOT NULL,
    name VARCHAR(200) NOT NULL,
    time_slot VARCHAR(20),
    note VARCHAR(500),
    matched_pid INT DEFAULT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    FOREIGN KEY (APDID) REFERENCES ai_parsed_day(APDID) ON DELETE CASCADE,
    FOREIGN KEY (matched_pid) REFERENCES poi(PID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS itinerary_item_option (
    IIOID INT AUTO_INCREMENT PRIMARY KEY,
    IIID INT NOT NULL,
    name VARCHAR(200) NOT NULL,
    latitude DECIMAL(10,7),
    longitude DECIMAL(10,7),
    is_selected BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (IIID) REFERENCES itinerary_item(IIID) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS image_asset (
    IAID INT AUTO_INCREMENT PRIMARY KEY,
    AID INT NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    original_filename VARCHAR(255),
    content_type VARCHAR(50),
    tags TEXT,
    ai_description TEXT,
    matched_pid INT DEFAULT NULL,
    tag_status VARCHAR(20) DEFAULT 'pending',
    uploaded_by INT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (AID) REFERENCES agency(AID),
    FOREIGN KEY (matched_pid) REFERENCES poi(PID),
    FOREIGN KEY (uploaded_by) REFERENCES staff_user(UID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS agency_export_template (
    AETID INT AUTO_INCREMENT PRIMARY KEY,
    AID INT NOT NULL,
    name VARCHAR(100) NOT NULL,
    file_path VARCHAR(255) NOT NULL,
    is_default TINYINT(1) NOT NULL DEFAULT 0,
    uploaded_by INT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_aet_agency FOREIGN KEY (AID) REFERENCES agency(AID),
    CONSTRAINT fk_aet_uploader FOREIGN KEY (uploaded_by) REFERENCES staff_user(UID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS poi_override (
    OID INT AUTO_INCREMENT PRIMARY KEY,
    AID INT NOT NULL,
    original_pid INT NOT NULL,
    override_pid INT DEFAULT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_agency_original (AID, original_pid),
    FOREIGN KEY (AID) REFERENCES agency(AID),
    FOREIGN KEY (original_pid) REFERENCES poi(PID) ON DELETE CASCADE,
    FOREIGN KEY (override_pid) REFERENCES poi(PID) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS currency (
    CID INT AUTO_INCREMENT PRIMARY KEY,
    AID INT DEFAULT NULL,
    code VARCHAR(10) NOT NULL,
    name VARCHAR(50),
    rate_to_twd DECIMAL(12,6) NOT NULL DEFAULT 1.000000,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (AID) REFERENCES agency(AID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS margin_setting (
    MSID INT AUTO_INCREMENT PRIMARY KEY,
    AID INT NOT NULL,
    name VARCHAR(50) NOT NULL,
    trade_markup_pct DECIMAL(6,2) NOT NULL DEFAULT 0,
    retail_markup_pct DECIMAL(6,2) NOT NULL DEFAULT 0,
    rebate_pct DECIMAL(6,2) NOT NULL DEFAULT 0,
    is_default TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (AID) REFERENCES agency(AID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS quotation (
    QID INT AUTO_INCREMENT PRIMARY KEY,
    ITID INT NOT NULL,
    AID INT NOT NULL,
    version INT NOT NULL DEFAULT 1,
    MSID INT DEFAULT NULL,
    group_size INT NOT NULL DEFAULT 1,
    status VARCHAR(20) NOT NULL DEFAULT 'draft',
    note TEXT,
    expires_at DATETIME DEFAULT NULL,
    created_by INT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    locked_at DATETIME DEFAULT NULL,
    confirmed_at DATETIME DEFAULT NULL,
    FOREIGN KEY (ITID) REFERENCES itinerary(ITID) ON DELETE CASCADE,
    FOREIGN KEY (AID) REFERENCES agency(AID),
    FOREIGN KEY (MSID) REFERENCES margin_setting(MSID),
    FOREIGN KEY (created_by) REFERENCES staff_user(UID),
    UNIQUE KEY uk_quotation_version (ITID, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS quotation_line (
    QLID INT AUTO_INCREMENT PRIMARY KEY,
    QID INT NOT NULL,
    CPID INT DEFAULT NULL,
    item_name VARCHAR(150) NOT NULL,
    category VARCHAR(20) NOT NULL DEFAULT 'other',
    currency_code VARCHAR(10) NOT NULL DEFAULT 'TWD',
    exchange_rate DECIMAL(12,6) NOT NULL DEFAULT 1.000000,
    unit_price DECIMAL(12,2) NOT NULL DEFAULT 0,
    quantity INT NOT NULL DEFAULT 1,
    fuel_surcharge DECIMAL(12,2) NOT NULL DEFAULT 0,
    tax_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
    foc_ratio INT NOT NULL DEFAULT 0,
    foc_qty INT NOT NULL DEFAULT 0,
    refundable TINYINT(1) NOT NULL DEFAULT 1,
    net_cost DECIMAL(12,2) NOT NULL DEFAULT 0,
    trade_price DECIMAL(12,2) NOT NULL DEFAULT 0,
    retail_price DECIMAL(12,2) NOT NULL DEFAULT 0,
    rebate_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
    profit_trade DECIMAL(12,2) NOT NULL DEFAULT 0,
    profit_retail DECIMAL(12,2) NOT NULL DEFAULT 0,
    note TEXT,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (QID) REFERENCES quotation(QID) ON DELETE CASCADE,
    FOREIGN KEY (CPID) REFERENCES component(CPID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS quotation_line_tier (
    QLTID INT AUTO_INCREMENT PRIMARY KEY,
    QLID INT NOT NULL,
    min_qty INT NOT NULL,
    max_qty INT DEFAULT NULL,
    price DECIMAL(12,2) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    FOREIGN KEY (QLID) REFERENCES quotation_line(QLID) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS price_tier_template (
    PTTID INT AUTO_INCREMENT PRIMARY KEY,
    AID INT NOT NULL,
    name VARCHAR(100) NOT NULL,
    created_by INT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (AID) REFERENCES agency(AID),
    FOREIGN KEY (created_by) REFERENCES staff_user(UID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS price_tier_template_row (
    PTTRID INT AUTO_INCREMENT PRIMARY KEY,
    PTTID INT NOT NULL,
    min_qty INT NOT NULL,
    max_qty INT DEFAULT NULL,
    price DECIMAL(12,2) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    FOREIGN KEY (PTTID) REFERENCES price_tier_template(PTTID) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS quotation_group_tier (
    QGTID INT AUTO_INCREMENT PRIMARY KEY,
    QID INT NOT NULL,
    min_qty INT NOT NULL,
    max_qty INT DEFAULT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    total_net_cost DECIMAL(14,2) NOT NULL DEFAULT 0,
    net_cost_per_pax DECIMAL(14,2) NOT NULL DEFAULT 0,
    trade_price_per_pax DECIMAL(14,2) NOT NULL DEFAULT 0,
    retail_price_per_pax DECIMAL(14,2) NOT NULL DEFAULT 0,
    margin_rate_pct DECIMAL(6,2) NOT NULL DEFAULT 0,
    FOREIGN KEY (QID) REFERENCES quotation(QID) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS country_city_code (
    CCID INT AUTO_INCREMENT PRIMARY KEY,
    type VARCHAR(10) NOT NULL,
    code VARCHAR(10) NOT NULL,
    name VARCHAR(50) NOT NULL,
    country_code VARCHAR(10) DEFAULT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE IF NOT EXISTS google_distance_cache (
    GDCID INT AUTO_INCREMENT PRIMARY KEY,
    from_lat DECIMAL(8,5) NOT NULL,
    from_lng DECIMAL(8,5) NOT NULL,
    to_lat DECIMAL(8,5) NOT NULL,
    to_lng DECIMAL(8,5) NOT NULL,
    mode VARCHAR(20) NOT NULL,
    distance_km DECIMAL(19,2) NULL,
    duration_min INT NULL,
    cached_at DATETIME NULL,
    UNIQUE KEY uq_google_distance_cache (from_lat, from_lng, to_lat, to_lng, mode)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS google_polyline_cache (
    GPCID INT AUTO_INCREMENT PRIMARY KEY,
    from_lat DECIMAL(8,5) NOT NULL,
    from_lng DECIMAL(8,5) NOT NULL,
    to_lat DECIMAL(8,5) NOT NULL,
    to_lng DECIMAL(8,5) NOT NULL,
    mode VARCHAR(20) NOT NULL,
    polyline TEXT NULL,
    cached_at DATETIME NULL,
    UNIQUE KEY uq_google_polyline_cache (from_lat, from_lng, to_lat, to_lng, mode)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 二、先記錄「這批異動之前」的狀態 (決定要不要跑一次性的資料搬移)
-- ============================================================
SET @fresh_perm = (SELECT IF(COUNT(*)=0, 1, 0) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='staff_user' AND COLUMN_NAME='is_active');
SET @fresh_trade = (SELECT IF(COUNT(*)=0, 1, 0) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='quotation' AND COLUMN_NAME='trade_markup_value');
SET @fresh_rebate = (SELECT IF(COUNT(*)=0, 1, 0) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='quotation' AND COLUMN_NAME='rebate_pct');
SET @fresh_split = (SELECT IF(COUNT(*)=0, 1, 0) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='margin_setting' AND COLUMN_NAME='default_pricing');

-- 若專案有用 Flyway 且 V2 (拆分預設規則) 還沒被套用, 就把 default_pricing/default_tier 交給 Flyway 建立,
-- 避免這裡先加了、Flyway 之後再加一次而啟動失敗。沒用 Flyway / V2 已套用 -> @flyway_v2_pending 維持 0。
SET @flyway_v2_pending = 0;
-- Flyway V4 (itinerary_item.ai_description) / V5 (ai_parsed_item.description): 尚未套用就交給 Flyway 建, 避免重複加欄位導致啟動失敗
SET @flyway_v4_pending = 0;
SET @flyway_v5_pending = 0;

-- ============================================================
-- 三、舊欄位改名: quotation_line.basic_quote -> gross_cost (只有舊資料庫才會執行)
-- ============================================================
SET @s = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='quotation_line' AND COLUMN_NAME='basic_quote') > 0
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='quotation_line' AND COLUMN_NAME='gross_cost') = 0,
    'ALTER TABLE `quotation_line` CHANGE COLUMN `basic_quote` `gross_cost` DECIMAL(14,2) NOT NULL DEFAULT 0',
    'DO 0'));
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;

-- ============================================================
-- 四、欄位 (不存在才新增)
-- ============================================================
-- staff_user
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `staff_user` ADD COLUMN `is_active` TINYINT(1) NOT NULL DEFAULT 1', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='staff_user' AND COLUMN_NAME='is_active');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
-- poi
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `poi` ADD COLUMN `original_name` VARCHAR(500) DEFAULT NULL', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='poi' AND COLUMN_NAME='original_name');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `poi` ADD COLUMN `agency_price` DECIMAL(10,2) DEFAULT NULL', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='poi' AND COLUMN_NAME='agency_price');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `poi` ADD COLUMN `supplier_contact` VARCHAR(100) DEFAULT NULL', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='poi' AND COLUMN_NAME='supplier_contact');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `poi` ADD COLUMN `supplier_notes` TEXT DEFAULT NULL', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='poi' AND COLUMN_NAME='supplier_notes');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
-- itinerary
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `itinerary` ADD COLUMN `template_style` VARCHAR(30) DEFAULT ''default''', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='itinerary' AND COLUMN_NAME='template_style');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `itinerary` ADD COLUMN `region` VARCHAR(50) DEFAULT NULL', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='itinerary' AND COLUMN_NAME='region');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `itinerary` ADD COLUMN `description` TEXT NULL', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='itinerary' AND COLUMN_NAME='description');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `itinerary` ADD COLUMN `pinned` TINYINT(1) NOT NULL DEFAULT 0', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='itinerary' AND COLUMN_NAME='pinned');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `itinerary` ADD COLUMN `pinned_at` DATETIME NULL', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='itinerary' AND COLUMN_NAME='pinned_at');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `itinerary` ADD COLUMN `is_locked` TINYINT(1) NOT NULL DEFAULT 0', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='itinerary' AND COLUMN_NAME='is_locked');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `itinerary` ADD COLUMN `locked_by` INT DEFAULT NULL', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='itinerary' AND COLUMN_NAME='locked_by');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `itinerary` ADD COLUMN `locked_at` DATETIME DEFAULT NULL', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='itinerary' AND COLUMN_NAME='locked_at');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
-- itinerary_day
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `itinerary_day` ADD COLUMN `start_time` TIME DEFAULT ''09:00:00''', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='itinerary_day' AND COLUMN_NAME='start_time');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `itinerary_day` ADD COLUMN `transport_mode` VARCHAR(20) DEFAULT ''driving''', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='itinerary_day' AND COLUMN_NAME='transport_mode');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `itinerary_day` ADD COLUMN `planned_cities` VARCHAR(255) NULL', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='itinerary_day' AND COLUMN_NAME='planned_cities');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
-- itinerary_item
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `itinerary_item` ADD COLUMN `latitude` DECIMAL(10,7) DEFAULT NULL', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='itinerary_item' AND COLUMN_NAME='latitude');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `itinerary_item` ADD COLUMN `longitude` DECIMAL(10,7) DEFAULT NULL', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='itinerary_item' AND COLUMN_NAME='longitude');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `itinerary_item` ADD COLUMN `item_country` VARCHAR(50) DEFAULT NULL', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='itinerary_item' AND COLUMN_NAME='item_country');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `itinerary_item` ADD COLUMN `item_region` VARCHAR(50) DEFAULT NULL', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='itinerary_item' AND COLUMN_NAME='item_region');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `itinerary_item` ADD COLUMN `time_slot` VARCHAR(20) DEFAULT NULL', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='itinerary_item' AND COLUMN_NAME='time_slot');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `itinerary_item` ADD COLUMN `show_on_map` TINYINT(1) NOT NULL DEFAULT 1', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='itinerary_item' AND COLUMN_NAME='show_on_map');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `itinerary_item` ADD COLUMN `from_location` VARCHAR(150) DEFAULT NULL', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='itinerary_item' AND COLUMN_NAME='from_location');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `itinerary_item` ADD COLUMN `from_address` VARCHAR(300) DEFAULT NULL', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='itinerary_item' AND COLUMN_NAME='from_address');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `itinerary_item` ADD COLUMN `to_location` VARCHAR(150) DEFAULT NULL', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='itinerary_item' AND COLUMN_NAME='to_location');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `itinerary_item` ADD COLUMN `to_address` VARCHAR(300) DEFAULT NULL', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='itinerary_item' AND COLUMN_NAME='to_address');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `itinerary_item` ADD COLUMN `transport_method` VARCHAR(30) DEFAULT NULL', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='itinerary_item' AND COLUMN_NAME='transport_method');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `itinerary_item` ADD COLUMN `transport_number` VARCHAR(50) NULL', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='itinerary_item' AND COLUMN_NAME='transport_number');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `itinerary_item` ADD COLUMN `commute_duration` VARCHAR(50) DEFAULT NULL', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='itinerary_item' AND COLUMN_NAME='commute_duration');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `itinerary_item` ADD COLUMN `commute_duration_min` INT DEFAULT NULL', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='itinerary_item' AND COLUMN_NAME='commute_duration_min');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `itinerary_item` ADD COLUMN `excluded_image_ids` VARCHAR(500) DEFAULT NULL', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='itinerary_item' AND COLUMN_NAME='excluded_image_ids');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `itinerary_item` ADD COLUMN `flight_direction` VARCHAR(10) NULL', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='itinerary_item' AND COLUMN_NAME='flight_direction');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
-- ai_import
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `ai_import` ADD COLUMN `template_style` VARCHAR(30) DEFAULT ''default''', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ai_import' AND COLUMN_NAME='template_style');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `ai_import` ADD COLUMN `suggested_title` VARCHAR(100) DEFAULT NULL', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ai_import' AND COLUMN_NAME='suggested_title');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `ai_import` ADD COLUMN `suggested_country` VARCHAR(50) DEFAULT NULL', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ai_import' AND COLUMN_NAME='suggested_country');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `ai_import` ADD COLUMN `suggested_region` VARCHAR(50) DEFAULT NULL', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ai_import' AND COLUMN_NAME='suggested_region');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `ai_import` ADD COLUMN `extra_context` TEXT DEFAULT NULL', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ai_import' AND COLUMN_NAME='extra_context');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
-- ai_parsed_item
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `ai_parsed_item` ADD COLUMN `name_en` VARCHAR(200) DEFAULT NULL', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ai_parsed_item' AND COLUMN_NAME='name_en');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `ai_parsed_item` ADD COLUMN `stay_minutes` INT DEFAULT NULL', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ai_parsed_item' AND COLUMN_NAME='stay_minutes');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `ai_parsed_item` ADD COLUMN `item_country` VARCHAR(50) DEFAULT NULL', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ai_parsed_item' AND COLUMN_NAME='item_country');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `ai_parsed_item` ADD COLUMN `item_region` VARCHAR(50) DEFAULT NULL', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ai_parsed_item' AND COLUMN_NAME='item_region');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `ai_parsed_item` ADD COLUMN `from_location` VARCHAR(100) NULL', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ai_parsed_item' AND COLUMN_NAME='from_location');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `ai_parsed_item` ADD COLUMN `to_location` VARCHAR(100) NULL', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ai_parsed_item' AND COLUMN_NAME='to_location');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `ai_parsed_item` ADD COLUMN `transport_method` VARCHAR(50) NULL', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ai_parsed_item' AND COLUMN_NAME='transport_method');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `ai_parsed_item` ADD COLUMN `transport_number` VARCHAR(50) NULL', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ai_parsed_item' AND COLUMN_NAME='transport_number');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `ai_parsed_item` ADD COLUMN `departure_time` TIME NULL', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ai_parsed_item' AND COLUMN_NAME='departure_time');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `ai_parsed_item` ADD COLUMN `arrival_time` TIME NULL', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ai_parsed_item' AND COLUMN_NAME='arrival_time');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
-- itinerary: arrange_mode (行程排程模式)
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `itinerary` ADD COLUMN `arrange_mode` VARCHAR(20) NULL', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='itinerary' AND COLUMN_NAME='arrange_mode');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
-- itinerary_item: ai_description (Flyway V4)
SET @s = (SELECT IF(COUNT(*)=0 AND @flyway_v4_pending=0, 'ALTER TABLE `itinerary_item` ADD COLUMN `ai_description` TEXT NULL', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='itinerary_item' AND COLUMN_NAME='ai_description');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
-- ai_parsed_item: description (Flyway V5)
SET @s = (SELECT IF(COUNT(*)=0 AND @flyway_v5_pending=0, 'ALTER TABLE `ai_parsed_item` ADD COLUMN `description` TEXT NULL', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ai_parsed_item' AND COLUMN_NAME='description');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
-- component
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `component` ADD COLUMN `currency_code` VARCHAR(10) NOT NULL DEFAULT ''TWD''', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='component' AND COLUMN_NAME='currency_code');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `component` ADD COLUMN `refundable` TINYINT(1) NOT NULL DEFAULT 1', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='component' AND COLUMN_NAME='refundable');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `component` ADD COLUMN `cost_type` VARCHAR(20) NOT NULL DEFAULT ''PER_PAX''', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='component' AND COLUMN_NAME='cost_type');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
-- agency_export_template
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `agency_export_template` ADD COLUMN `template_type` VARCHAR(20) NOT NULL DEFAULT ''CUSTOMER''', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='agency_export_template' AND COLUMN_NAME='template_type');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
-- margin_setting
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `margin_setting` ADD COLUMN `trade_formula` VARCHAR(500) DEFAULT NULL', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='margin_setting' AND COLUMN_NAME='trade_formula');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `margin_setting` ADD COLUMN `retail_formula` VARCHAR(500) DEFAULT NULL', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='margin_setting' AND COLUMN_NAME='retail_formula');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `margin_setting` ADD COLUMN `rebate_formula` VARCHAR(500) DEFAULT NULL', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='margin_setting' AND COLUMN_NAME='rebate_formula');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `margin_setting` ADD COLUMN `basic_formula` VARCHAR(500) DEFAULT NULL', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='margin_setting' AND COLUMN_NAME='basic_formula');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `margin_setting` ADD COLUMN `np_formula` VARCHAR(500) DEFAULT NULL', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='margin_setting' AND COLUMN_NAME='np_formula');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `margin_setting` ADD COLUMN `team_formula` VARCHAR(500) DEFAULT NULL', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='margin_setting' AND COLUMN_NAME='team_formula');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
-- quotation
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `quotation` ADD COLUMN `formula_mode` VARCHAR(10) NOT NULL DEFAULT ''preset''', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='quotation' AND COLUMN_NAME='formula_mode');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `quotation` ADD COLUMN `custom_basic_formula` VARCHAR(500) DEFAULT NULL', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='quotation' AND COLUMN_NAME='custom_basic_formula');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `quotation` ADD COLUMN `custom_trade_formula` VARCHAR(500) DEFAULT NULL', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='quotation' AND COLUMN_NAME='custom_trade_formula');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `quotation` ADD COLUMN `custom_retail_formula` VARCHAR(500) DEFAULT NULL', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='quotation' AND COLUMN_NAME='custom_retail_formula');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `quotation` ADD COLUMN `custom_rebate_formula` VARCHAR(500) DEFAULT NULL', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='quotation' AND COLUMN_NAME='custom_rebate_formula');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `quotation` ADD COLUMN `basic_markup_mode` VARCHAR(10) NOT NULL DEFAULT ''PERCENT''', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='quotation' AND COLUMN_NAME='basic_markup_mode');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `quotation` ADD COLUMN `basic_markup_value` DECIMAL(14,2) NOT NULL DEFAULT 0', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='quotation' AND COLUMN_NAME='basic_markup_value');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `quotation` ADD COLUMN `trade_markup_mode` VARCHAR(10) NOT NULL DEFAULT ''PERCENT''', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='quotation' AND COLUMN_NAME='trade_markup_mode');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `quotation` ADD COLUMN `trade_markup_value` DECIMAL(14,2) NOT NULL DEFAULT 0', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='quotation' AND COLUMN_NAME='trade_markup_value');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `quotation` ADD COLUMN `retail_markup_mode` VARCHAR(10) NOT NULL DEFAULT ''PERCENT''', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='quotation' AND COLUMN_NAME='retail_markup_mode');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `quotation` ADD COLUMN `retail_markup_value` DECIMAL(14,2) NOT NULL DEFAULT 0', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='quotation' AND COLUMN_NAME='retail_markup_value');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `quotation` ADD COLUMN `rebate_pct` DECIMAL(6,2) NOT NULL DEFAULT 0', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='quotation' AND COLUMN_NAME='rebate_pct');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `quotation` ADD COLUMN `rebate_mode` VARCHAR(10) NOT NULL DEFAULT ''PERCENT''', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='quotation' AND COLUMN_NAME='rebate_mode');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `quotation` ADD COLUMN `group_tier_headcount_mode` VARCHAR(10) NOT NULL DEFAULT ''LOWER''', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='quotation' AND COLUMN_NAME='group_tier_headcount_mode');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `quotation` ADD COLUMN `tier_formula_mode` VARCHAR(10) NOT NULL DEFAULT ''custom''', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='quotation' AND COLUMN_NAME='tier_formula_mode');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `quotation` ADD COLUMN `tier_MSID` INT NULL', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='quotation' AND COLUMN_NAME='tier_MSID');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
-- quotation_line
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `quotation_line` ADD COLUMN `cost_type` VARCHAR(20) NOT NULL DEFAULT ''PER_PAX''', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='quotation_line' AND COLUMN_NAME='cost_type');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `quotation_line` ADD COLUMN `source_item_id` INT DEFAULT NULL', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='quotation_line' AND COLUMN_NAME='source_item_id');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `quotation_line` ADD COLUMN `gross_cost` DECIMAL(14,2) NOT NULL DEFAULT 0', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='quotation_line' AND COLUMN_NAME='gross_cost');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `quotation_line` ADD COLUMN `basic_price` DECIMAL(14,2) NOT NULL DEFAULT 0', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='quotation_line' AND COLUMN_NAME='basic_price');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `quotation_line` ADD COLUMN `tier_managed` TINYINT(1) NOT NULL DEFAULT 0', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='quotation_line' AND COLUMN_NAME='tier_managed');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
-- quotation_group_tier
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `quotation_group_tier` ADD COLUMN `currency` VARCHAR(10) NOT NULL DEFAULT ''TWD''', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='quotation_group_tier' AND COLUMN_NAME='currency');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `quotation_group_tier` ADD COLUMN `misc_value` DECIMAL(14,2) NOT NULL DEFAULT 0', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='quotation_group_tier' AND COLUMN_NAME='misc_value');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `quotation_group_tier` ADD COLUMN `misc_value_twd` DECIMAL(14,2) NOT NULL DEFAULT 0', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='quotation_group_tier' AND COLUMN_NAME='misc_value_twd');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `quotation_group_tier` ADD COLUMN `np_formula` VARCHAR(500) NOT NULL DEFAULT ''''', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='quotation_group_tier' AND COLUMN_NAME='np_formula');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `quotation_group_tier` ADD COLUMN `np_result_twd` DECIMAL(14,2) NOT NULL DEFAULT 0', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='quotation_group_tier' AND COLUMN_NAME='np_result_twd');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `quotation_group_tier` ADD COLUMN `team_formula` VARCHAR(500) NOT NULL DEFAULT ''''', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='quotation_group_tier' AND COLUMN_NAME='team_formula');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `quotation_group_tier` ADD COLUMN `team_result_twd` DECIMAL(14,2) NOT NULL DEFAULT 0', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='quotation_group_tier' AND COLUMN_NAME='team_result_twd');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `quotation_group_tier` ADD COLUMN `variable_cost_per_person_twd` DECIMAL(14,2) NOT NULL DEFAULT 0', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='quotation_group_tier' AND COLUMN_NAME='variable_cost_per_person_twd');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
-- country_city_code
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `country_city_code` ADD COLUMN `sort_order` INT NOT NULL DEFAULT 999', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='country_city_code' AND COLUMN_NAME='sort_order');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
-- margin_setting 的兩組獨立預設 (Flyway V2 尚未套用時交給 Flyway, 見上方說明)
SET @s = (SELECT IF(COUNT(*)=0 AND @flyway_v2_pending=0, 'ALTER TABLE `margin_setting` ADD COLUMN `default_pricing` TINYINT(1) NOT NULL DEFAULT 0', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='margin_setting' AND COLUMN_NAME='default_pricing');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0 AND @flyway_v2_pending=0, 'ALTER TABLE `margin_setting` ADD COLUMN `default_tier` TINYINT(1) NOT NULL DEFAULT 0', 'DO 0') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='margin_setting' AND COLUMN_NAME='default_tier');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;

-- ============================================================
-- 五、欄位型別調整 (重複執行無副作用)
-- ============================================================
-- 員工角色改成可多選 (逗號分隔), 原 VARCHAR(20) 放不下
ALTER TABLE staff_user MODIFY COLUMN role VARCHAR(60) DEFAULT 'VIEWER';

-- ============================================================
-- 六、索引與外鍵 (不存在才新增)
-- ============================================================
SET @s = (SELECT IF(COUNT(*)=0, 'CREATE INDEX `idx_aet_agency` ON `agency_export_template` (AID)', 'DO 0') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='agency_export_template' AND INDEX_NAME='idx_aet_agency');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'CREATE INDEX `idx_currency_code` ON `currency` (code)', 'DO 0') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='currency' AND INDEX_NAME='idx_currency_code');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'CREATE INDEX `idx_margin_agency` ON `margin_setting` (AID)', 'DO 0') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='margin_setting' AND INDEX_NAME='idx_margin_agency');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'CREATE INDEX `idx_quotation_itinerary` ON `quotation` (ITID)', 'DO 0') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='quotation' AND INDEX_NAME='idx_quotation_itinerary');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'CREATE INDEX `idx_qline_quotation` ON `quotation_line` (QID)', 'DO 0') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='quotation_line' AND INDEX_NAME='idx_qline_quotation');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'CREATE INDEX `idx_qline_source_item` ON `quotation_line` (source_item_id)', 'DO 0') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='quotation_line' AND INDEX_NAME='idx_qline_source_item');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'CREATE INDEX `idx_qlt_line` ON `quotation_line_tier` (QLID)', 'DO 0') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='quotation_line_tier' AND INDEX_NAME='idx_qlt_line');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'CREATE INDEX `idx_ptt_agency` ON `price_tier_template` (AID)', 'DO 0') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='price_tier_template' AND INDEX_NAME='idx_ptt_agency');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'CREATE INDEX `idx_pttr_template` ON `price_tier_template_row` (PTTID)', 'DO 0') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='price_tier_template_row' AND INDEX_NAME='idx_pttr_template');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'CREATE INDEX `idx_qgt_quotation` ON `quotation_group_tier` (QID)', 'DO 0') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='quotation_group_tier' AND INDEX_NAME='idx_qgt_quotation');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'CREATE INDEX `idx_country_city_code_name` ON `country_city_code` (name)', 'DO 0') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='country_city_code' AND INDEX_NAME='idx_country_city_code_name');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'CREATE INDEX `idx_country_city_code_code` ON `country_city_code` (code)', 'DO 0') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='country_city_code' AND INDEX_NAME='idx_country_city_code_code');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `itinerary` ADD CONSTRAINT `fk_itinerary_locked_by` FOREIGN KEY (`locked_by`) REFERENCES `staff_user`(`UID`)', 'DO 0') FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='itinerary' AND COLUMN_NAME='locked_by' AND REFERENCED_TABLE_NAME IS NOT NULL);
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `quotation_line` ADD CONSTRAINT `fk_qline_source_item` FOREIGN KEY (`source_item_id`) REFERENCES `itinerary_item`(`IIID`) ON DELETE SET NULL', 'DO 0') FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='quotation_line' AND COLUMN_NAME='source_item_id' AND REFERENCED_TABLE_NAME IS NOT NULL);
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;

-- ============================================================
-- 七、一次性資料搬移 (只有「欄位是這次才新增」時才會執行, 重複匯入不會再動資料)
-- ============================================================
-- 角色遷移: PM -> ADMIN, OP -> EDITOR (權限矩陣上線前的舊資料)
SET @s = IF(@fresh_perm=1, 'UPDATE staff_user SET role = ''ADMIN'' WHERE role = ''PM''', 'DO 0');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
SET @s = IF(@fresh_perm=1, 'UPDATE staff_user SET role = ''EDITOR'' WHERE role = ''OP''', 'DO 0');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
-- 舊範本一律視為 CUSTOMER (客戶版)
UPDATE agency_export_template SET template_type = 'CUSTOMER' WHERE template_type IS NULL OR template_type = '';
-- 同業/直售加成初始值: 帶入原本掛的加成規則
SET @s = IF(@fresh_trade=1, 'UPDATE quotation q JOIN margin_setting ms ON ms.MSID = q.MSID SET q.trade_markup_value = ms.trade_markup_pct, q.retail_markup_value = ms.retail_markup_pct WHERE q.MSID IS NOT NULL', 'DO 0');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
-- 退傭%初始值: 帶入原本掛的加成規則
SET @s = IF(@fresh_rebate=1, 'UPDATE quotation q JOIN margin_setting ms ON ms.MSID = q.MSID SET q.rebate_pct = ms.rebate_pct WHERE q.MSID IS NOT NULL', 'DO 0');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
-- 舊的單一 is_default 拆成 default_pricing / default_tier 兩組
SET @s = IF(@fresh_split=1 AND @flyway_v2_pending=0, 'UPDATE margin_setting SET default_pricing = CASE WHEN is_default = 1 AND NOT ((basic_formula IS NULL AND trade_formula IS NULL AND retail_formula IS NULL AND rebate_formula IS NULL) AND (np_formula IS NOT NULL OR team_formula IS NOT NULL)) THEN 1 ELSE 0 END, default_tier = CASE WHEN is_default = 1 AND (np_formula IS NOT NULL OR team_formula IS NOT NULL) THEN 1 ELSE 0 END', 'DO 0');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;

-- ============================================================
-- 八、預設資料 (已存在就不重複新增)
-- ============================================================
-- 平台共用幣別

-- 國家 / 城市通用代碼 (依 type + code 判斷, 已存在就跳過)

-- ============================================================
-- 一次性資料修正 (原 Flyway V3, 冪等): 景點被旅行社 override 之後, 把圖片綁定搬到專屬複本 PID
-- ============================================================
UPDATE image_asset ia
JOIN poi_override po ON po.AID = ia.AID AND po.original_pid = ia.matched_pid
SET ia.matched_pid = po.override_pid
WHERE po.override_pid IS NOT NULL;

-- ============================================================
-- 九、完成提示
-- ============================================================
SELECT '完成: 資料庫結構已是最新版本 (本機版, 不使用 Flyway)' AS result;
SELECT TABLE_NAME AS table_name, COUNT(*) AS column_count FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() GROUP BY TABLE_NAME ORDER BY TABLE_NAME;
