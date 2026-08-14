-- ============================================================
-- Useful Travel 智慧行程規劃平台 - 資料庫設計 (MySQL 8+)
-- 延續原範本 (Food_Pangolin_Java) 的技術棧: Spring Boot + JPA + MySQL
-- 命名慣例沿用原範本: 表名小寫底線, 主鍵用 XX_ID 縮寫大寫欄位
-- ============================================================

CREATE DATABASE IF NOT EXISTS useful_travel
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE useful_travel;

-- ------------------------------------------------------------
-- 1. 租戶 / 旅行社 (B2B SaaS 多租戶核心表)
-- ------------------------------------------------------------
CREATE TABLE agency (
    AID INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,          -- 旅行社名稱
    license_no VARCHAR(50),              -- 旅行社執照字號
    contact_phone VARCHAR(20),
    contact_email VARCHAR(100),
    plan_type VARCHAR(20) DEFAULT 'trial', -- trial / standard / enterprise
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 2. 員工帳號 (OP / PM / 管理員) - 對應原範本 User
-- ------------------------------------------------------------
CREATE TABLE staff_user (
    UID INT AUTO_INCREMENT PRIMARY KEY,
    AID INT NOT NULL,
    name VARCHAR(50) NOT NULL,
    phone VARCHAR(20),
    account VARCHAR(50) NOT NULL UNIQUE,
    pw VARCHAR(255) NOT NULL,            -- 存 bcrypt hash, 不存明碼
    role VARCHAR(20) DEFAULT 'OP',       -- OP / PM / ADMIN
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (AID) REFERENCES agency(AID)
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 3. POI 資料庫 (景點 / 餐廳 / 休息站 / 飯店 共用主檔)
--    這是你信裡提到「景點與飯店庫」的核心
-- ------------------------------------------------------------
CREATE TABLE poi (
    PID INT AUTO_INCREMENT PRIMARY KEY,
    AID INT DEFAULT NULL,                -- NULL = 平台共用庫, 有值 = 該旅行社自建景點
    category VARCHAR(20) NOT NULL,       -- attraction / restaurant / hotel / rest_stop
    name VARCHAR(100) NOT NULL,
    country VARCHAR(50),
    city VARCHAR(50),
    address VARCHAR(255),
    latitude DECIMAL(10,7),
    longitude DECIMAL(10,7),
    suggested_stay_min INT DEFAULT 60,   -- 建議停留時間(分鐘)
    open_hours VARCHAR(255),             -- 可先存文字, 之後再拆表
    description TEXT,
    star_rating DECIMAL(2,1),            -- 飯店用: 星等
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (AID) REFERENCES agency(AID)
) ENGINE=InnoDB;

CREATE TABLE poi_image (
    IID INT AUTO_INCREMENT PRIMARY KEY,
    PID INT NOT NULL,
    image_url VARCHAR(500) NOT NULL,
    sort_order INT DEFAULT 0,
    FOREIGN KEY (PID) REFERENCES poi(PID) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 4. 行程主檔 - 對應原範本 Restaurant(主體) + OrderList(狀態)
-- ------------------------------------------------------------
CREATE TABLE itinerary (
    ITID INT AUTO_INCREMENT PRIMARY KEY,
    AID INT NOT NULL,
    created_by INT NOT NULL,             -- staff_user.UID
    title VARCHAR(100) NOT NULL,
    country VARCHAR(50),
    days_count INT NOT NULL DEFAULT 1,
    start_date DATE,
    end_date DATE,
    group_size INT,
    status VARCHAR(20) DEFAULT 'draft',  -- draft / confirmed / departed / completed
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (AID) REFERENCES agency(AID),
    FOREIGN KEY (created_by) REFERENCES staff_user(UID)
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 5. 每日行程 (Day 1, Day 2 ...)
-- ------------------------------------------------------------
CREATE TABLE itinerary_day (
    IDID INT AUTO_INCREMENT PRIMARY KEY,
    ITID INT NOT NULL,
    day_number INT NOT NULL,             -- 第幾天
    day_date DATE,
    theme VARCHAR(100),                  -- 當天主題, e.g. "京都古寺巡禮"
    FOREIGN KEY (ITID) REFERENCES itinerary(ITID) ON DELETE CASCADE,
    UNIQUE KEY uk_day (ITID, day_number)
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 6. 行程項目 (拖曳排版的最小單位) - 對應原範本 UserCart 的角色
--    每個項目可以是景點/餐廳/住宿/交通/自費活動
-- ------------------------------------------------------------
CREATE TABLE itinerary_item (
    IIID INT AUTO_INCREMENT PRIMARY KEY,
    IDID INT NOT NULL,
    PID INT DEFAULT NULL,                -- 對應 poi, 若是自訂項目可為 NULL
    item_type VARCHAR(20) NOT NULL,      -- attraction / meal / hotel / transport / optional / free_time
    custom_name VARCHAR(100),            -- 沒有對到 POI 時用的自訂名稱
    sort_order INT NOT NULL DEFAULT 0,   -- 拖曳排序用
    start_time TIME,
    end_time TIME,
    stay_duration_min INT,
    note TEXT,
    FOREIGN KEY (IDID) REFERENCES itinerary_day(IDID) ON DELETE CASCADE,
    FOREIGN KEY (PID) REFERENCES poi(PID)
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 7. 路段/拉車資訊 (兩個行程項目之間的交通計算結果快取)
--    用來做「反覆拉車 alert」與地圖路線繪製
-- ------------------------------------------------------------
CREATE TABLE route_segment (
    RSID INT AUTO_INCREMENT PRIMARY KEY,
    IDID INT NOT NULL,
    from_item_id INT NOT NULL,
    to_item_id INT NOT NULL,
    distance_km DECIMAL(6,2),
    duration_min INT,
    transport_mode VARCHAR(20) DEFAULT 'driving', -- driving / walking / transit
    is_backtrack BOOLEAN DEFAULT FALSE,  -- 迴頭路警示旗標
    calculated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (IDID) REFERENCES itinerary_day(IDID) ON DELETE CASCADE,
    FOREIGN KEY (from_item_id) REFERENCES itinerary_item(IIID),
    FOREIGN KEY (to_item_id) REFERENCES itinerary_item(IIID)
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 8. 元件庫 (航班/餐食等級/住宿等級/自費項目 - 可重複套用的標準元件)
-- ------------------------------------------------------------
CREATE TABLE component (
    CPID INT AUTO_INCREMENT PRIMARY KEY,
    AID INT NOT NULL,
    type VARCHAR(20) NOT NULL,           -- flight / meal / hotel_grade / optional_tour
    name VARCHAR(100) NOT NULL,
    default_price DECIMAL(10,2),
    description TEXT,
    FOREIGN KEY (AID) REFERENCES agency(AID)
) ENGINE=InnoDB;

CREATE TABLE itinerary_component (
    ICID INT AUTO_INCREMENT PRIMARY KEY,
    ITID INT NOT NULL,
    CPID INT NOT NULL,
    day_number INT,
    quantity INT DEFAULT 1,
    price_override DECIMAL(10,2),
    FOREIGN KEY (ITID) REFERENCES itinerary(ITID) ON DELETE CASCADE,
    FOREIGN KEY (CPID) REFERENCES component(CPID)
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 9. 企劃書輸出紀錄 (一鍵美化輸出的歷史檔)
-- ------------------------------------------------------------
CREATE TABLE export_history (
    EHID INT AUTO_INCREMENT PRIMARY KEY,
    ITID INT NOT NULL,
    format VARCHAR(10) NOT NULL,         -- pdf / pptx / docx
    file_url VARCHAR(500),
    generated_by INT,
    generated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (ITID) REFERENCES itinerary(ITID) ON DELETE CASCADE,
    FOREIGN KEY (generated_by) REFERENCES staff_user(UID)
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 10. AI 行程打碎解析 (Travel Itinerary Parser)
--     線控貼上文字/上傳文件後, AI 先解析成「暫存」資料讓人確認,
--     確認後才會轉成正式的 itinerary / itinerary_day / itinerary_item
-- ------------------------------------------------------------
CREATE TABLE ai_import (
    IPID INT AUTO_INCREMENT PRIMARY KEY,
    AID INT NOT NULL,
    created_by INT NOT NULL,             -- staff_user.UID
    source_type VARCHAR(20) NOT NULL DEFAULT 'text', -- text / pdf / docx / url / image
    raw_content MEDIUMTEXT,              -- 貼上的原始文字 (PDF/Word 之後解析出文字也存這裡)
    status VARCHAR(20) NOT NULL DEFAULT 'pending', -- pending / parsed / confirmed / failed
    error_message VARCHAR(500),
    result_itinerary_id INT DEFAULT NULL, -- 確認後產生的正式行程 ITID
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (AID) REFERENCES agency(AID),
    FOREIGN KEY (created_by) REFERENCES staff_user(UID),
    FOREIGN KEY (result_itinerary_id) REFERENCES itinerary(ITID)
) ENGINE=InnoDB;

CREATE TABLE ai_parsed_day (
    APDID INT AUTO_INCREMENT PRIMARY KEY,
    IPID INT NOT NULL,
    day_number INT NOT NULL,
    theme VARCHAR(200),
    FOREIGN KEY (IPID) REFERENCES ai_import(IPID) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE ai_parsed_item (
    APIID INT AUTO_INCREMENT PRIMARY KEY,
    APDID INT NOT NULL,
    item_type VARCHAR(20) NOT NULL,      -- attraction / meal / hotel / transport / highlight
    name VARCHAR(200) NOT NULL,
    time_slot VARCHAR(20),               -- morning / noon / afternoon / evening / breakfast / lunch / dinner
    note VARCHAR(500),                   -- 餐標/房型/交通方式/注意事項等原文描述
    matched_pid INT DEFAULT NULL,        -- 自動比對到公司 POI 資料庫的結果 (找不到就是 NULL)
    sort_order INT NOT NULL DEFAULT 0,
    FOREIGN KEY (APDID) REFERENCES ai_parsed_day(APDID) ON DELETE CASCADE,
    FOREIGN KEY (matched_pid) REFERENCES poi(PID)
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 11. 模板樣式記錄 (AI 解析時順便判斷原文件的風格, 輸出企劃書時套用同樣風格)
-- ------------------------------------------------------------
ALTER TABLE ai_import ADD COLUMN template_style VARCHAR(30) DEFAULT 'default';
ALTER TABLE itinerary ADD COLUMN template_style VARCHAR(30) DEFAULT 'default';

-- ------------------------------------------------------------
-- 12. 時間軸排版看板 (每日出發時間 + AI 預估停留時間)
-- ------------------------------------------------------------
ALTER TABLE itinerary_day ADD COLUMN start_time TIME DEFAULT '09:00:00';
ALTER TABLE ai_parsed_item ADD COLUMN stay_minutes INT DEFAULT NULL;

-- ------------------------------------------------------------
-- 13. AI 解析時順便判斷建議標題與國家地區, 確認頁面直接帶入
-- ------------------------------------------------------------
ALTER TABLE ai_import ADD COLUMN suggested_title VARCHAR(100) DEFAULT NULL;
ALTER TABLE ai_import ADD COLUMN suggested_country VARCHAR(50) DEFAULT NULL;

-- ------------------------------------------------------------
-- 14. 公司專屬資源庫 (成本價/同行價/供應商窗口/合作紀錄)
-- ------------------------------------------------------------
ALTER TABLE poi ADD COLUMN cost_price DECIMAL(10,2) DEFAULT NULL;      -- 成本價 (跟供應商談的價格)
ALTER TABLE poi ADD COLUMN agency_price DECIMAL(10,2) DEFAULT NULL;    -- 同行價/建議售價
ALTER TABLE poi ADD COLUMN supplier_contact VARCHAR(100) DEFAULT NULL; -- 供應商窗口 (姓名/電話/LINE等)
ALTER TABLE poi ADD COLUMN supplier_notes TEXT DEFAULT NULL;           -- 合作備註 (付款方式/取消政策等)

CREATE TABLE poi_cooperation_log (
    PCLID INT AUTO_INCREMENT PRIMARY KEY,
    PID INT NOT NULL,
    log_date DATE,
    note TEXT NOT NULL,
    created_by INT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (PID) REFERENCES poi(PID) ON DELETE CASCADE,
    FOREIGN KEY (created_by) REFERENCES staff_user(UID)
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 15. 國家/地區欄位分開 (行程層級)
-- ------------------------------------------------------------
ALTER TABLE itinerary ADD COLUMN region VARCHAR(50) DEFAULT NULL;       -- 地區/城市, 例如「花蓮」「北海道」
ALTER TABLE ai_import ADD COLUMN suggested_region VARCHAR(50) DEFAULT NULL;

-- ------------------------------------------------------------
-- 16. 行程項目自帶座標 (不一定要連結 POI 資料庫也能在地圖顯示) + 多選項 (例如同等級飯店 A或B或C)
-- ------------------------------------------------------------
ALTER TABLE itinerary_item ADD COLUMN latitude DECIMAL(10,7) DEFAULT NULL;
ALTER TABLE itinerary_item ADD COLUMN longitude DECIMAL(10,7) DEFAULT NULL;

CREATE TABLE itinerary_item_option (
    IIOID INT AUTO_INCREMENT PRIMARY KEY,
    IIID INT NOT NULL,
    name VARCHAR(200) NOT NULL,
    latitude DECIMAL(10,7),
    longitude DECIMAL(10,7),
    is_selected BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (IIID) REFERENCES itinerary_item(IIID) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 17. AI 解析時每個項目自己判斷國家/地區 (不要整個行程共用一個)
-- ------------------------------------------------------------
ALTER TABLE ai_parsed_item ADD COLUMN item_country VARCHAR(50) DEFAULT NULL;
ALTER TABLE ai_parsed_item ADD COLUMN item_region VARCHAR(50) DEFAULT NULL;

-- ------------------------------------------------------------
-- 18. 每日交通方式選擇 (走路/開車, 影響拉車時間計算)
-- ------------------------------------------------------------
ALTER TABLE itinerary_day ADD COLUMN transport_mode VARCHAR(20) DEFAULT 'driving';

-- ------------------------------------------------------------
-- 19. 圖片資源庫 (自家圖庫 + AI 自動標籤)
-- ------------------------------------------------------------
CREATE TABLE image_asset (
    IAID INT AUTO_INCREMENT PRIMARY KEY,
    AID INT NOT NULL,
    file_path VARCHAR(500) NOT NULL,        -- 實際存放在伺服器的相對路徑
    original_filename VARCHAR(255),
    content_type VARCHAR(50),               -- image/jpeg, image/png 等
    tags TEXT,                              -- AI 自動產生的標籤, 逗號分隔
    ai_description TEXT,                    -- AI 產生的圖片描述
    matched_pid INT DEFAULT NULL,           -- 自動/手動綁定到哪個 POI (可為 NULL, 表示還沒歸類)
    tag_status VARCHAR(20) DEFAULT 'pending', -- pending / tagged / failed
    uploaded_by INT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (AID) REFERENCES agency(AID),
    FOREIGN KEY (matched_pid) REFERENCES poi(PID),
    FOREIGN KEY (uploaded_by) REFERENCES staff_user(UID)
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 20. 行程項目也存自己的國家/地區 (從 AI 解析帶過來, 修正看板加入資料庫時抓到合併字串的問題)
-- ------------------------------------------------------------
ALTER TABLE itinerary_item ADD COLUMN item_country VARCHAR(50) DEFAULT NULL;
ALTER TABLE itinerary_item ADD COLUMN item_region VARCHAR(50) DEFAULT NULL;
