-- ============================================================
-- AI 行程建立: 出發/抵達機場+時間、行程說明、國家城市通用代碼
-- 請在 useful_travel_schema.sql (基礎表) 之後執行即可。
--
-- 背景:
--   1. AI 行程打碎解析畫面 (ai-import/new.html) 新增「出發機場／出發時間／抵達機場／抵達時間
--      (可 + 號加轉機)／行程說明」這組選填欄位, 讓使用者能給 AI 更精確的行程重點資訊 (例如天數、
--      實際去程回程時間), 而不是完全只靠 AI 自己從貼上的文字裡猜。這組資訊存成一段格式化文字,
--      跟原始文字一起送給 AI 當額外參考context, 也存進 ai_import 讓 review 頁面可以顯示回去給使用者看。
--   2. 「機場」直接當成 POI 資料庫的一種新 category (跟景點/餐廳/飯店/休息站同一張表), 不用另外建表——
--      name = 常用機場名稱 (例: 東京成田機場), original_name = 機場代碼 (例: NRT), 這樣機場輸入框可以
--      直接沿用既有的 POI 搜尋 (searchByKeyword 已經有比對 original_name), 不用重複打造一套比對邏輯。
--      category 欄位本身是自由文字 (VARCHAR(20), 沒有 ENUM 限制), 所以不需要另外 ALTER TABLE。
--   3. 「國家城市通用代碼」(例如 東京/TYO、日本/JP) 是全新的獨立對照表, 不影響現有 poi.country/
--      poi.city 欄位 (那兩個仍然是自由文字), 純粹提供一個查詢/輸入輔助用的代碼字典。
-- ============================================================

-- ------------------------------------------------------------
-- 1. ai_import: 新增「行程重點資訊」欄位 (格式化文字, 顯示用+送給AI當額外context用)
-- ------------------------------------------------------------
ALTER TABLE ai_import ADD COLUMN extra_context TEXT DEFAULT NULL AFTER raw_content;

-- ------------------------------------------------------------
-- 2. 國家城市通用代碼對照表 (全域共用, 不分旅行社)
-- ------------------------------------------------------------
CREATE TABLE country_city_code (
    CCID INT AUTO_INCREMENT PRIMARY KEY,
    type VARCHAR(10) NOT NULL,           -- country / city
    code VARCHAR(10) NOT NULL,           -- 例: JP、TYO
    name VARCHAR(50) NOT NULL,           -- 例: 日本、東京
    country_code VARCHAR(10) DEFAULT NULL, -- type=city 時, 所屬國家的 code (例: TYO 的 country_code = JP); type=country 這筆本身是 NULL
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_country_city_code_name ON country_city_code (name);
CREATE INDEX idx_country_city_code_code ON country_city_code (code);

-- 初始資料: 常見出團國家 + 主要城市 (業界慣用代碼, 不完全等同官方 IATA 城市代碼, 方便旅行社實務使用;
-- 之後可以自行在資料庫裡增修)
INSERT INTO country_city_code (type, code, name, country_code) VALUES
('country', 'JP', '日本', NULL),
('city', 'TYO', '東京', 'JP'),
('city', 'OSA', '大阪', 'JP'),
('city', 'KYO', '京都', 'JP'),
('city', 'NGO', '名古屋', 'JP'),
('city', 'CTS', '札幌', 'JP'),
('city', 'FUK', '福岡', 'JP'),
('city', 'OKA', '沖繩', 'JP'),
('city', 'HIJ', '廣島', 'JP'),
('city', 'SDJ', '仙台', 'JP'),
('city', 'KMQ', '金澤', 'JP'),

('country', 'TW', '台灣', NULL),
('city', 'TPE', '台北', 'TW'),
('city', 'KHH', '高雄', 'TW'),

('country', 'KR', '韓國', NULL),
('city', 'SEL', '首爾', 'KR'),
('city', 'PUS', '釜山', 'KR'),
('city', 'CJU', '濟州島', 'KR'),

('country', 'HK', '香港', NULL),
('country', 'MO', '澳門', NULL),

('country', 'CN', '中國', NULL),
('city', 'SHA', '上海', 'CN'),
('city', 'BJS', '北京', 'CN'),

('country', 'TH', '泰國', NULL),
('city', 'BKK', '曼谷', 'TH'),
('city', 'CNX', '清邁', 'TH'),
('city', 'HKT', '普吉島', 'TH'),

('country', 'SG', '新加坡', NULL),
('country', 'MY', '馬來西亞', NULL),
('city', 'KUL', '吉隆坡', 'MY'),

('country', 'PH', '菲律賓', NULL),
('city', 'MNL', '馬尼拉', 'PH'),
('city', 'CEB', '宿霧', 'PH'),

('country', 'ID', '印尼', NULL),
('city', 'DPS', '峇里島', 'ID'),
('city', 'JKT', '雅加達', 'ID'),

('country', 'VN', '越南', NULL),
('city', 'SGN', '胡志明市', 'VN'),
('city', 'HAN', '河內', 'VN'),
('city', 'DAD', '峴港', 'VN'),

('country', 'US', '美國', NULL),
('city', 'LAX', '洛杉磯', 'US'),
('city', 'SFO', '舊金山', 'US'),
('city', 'NYC', '紐約', 'US'),

('country', 'GB', '英國', NULL),
('city', 'LON', '倫敦', 'GB'),

('country', 'FR', '法國', NULL),
('city', 'PAR', '巴黎', 'FR'),

('country', 'AU', '澳洲', NULL),
('city', 'SYD', '雪梨', 'AU'),

('country', 'AE', '阿聯', NULL),
('city', 'DXB', '杜拜', 'AE');
