-- ============================================================
-- UsefulTravel 合併 Migration Script (完整版)
-- 合併自: migration_agency_export_template.sql + migration_quotation.sql
--        + migration_permissions.sql + migration_price_tier.sql + migration_quick_quote.sql
-- 使用方式: 先跑過 useful_travel_schema.sql (基礎表) 之後, 執行這一份即可, 不用再分開跑五次。
-- 內容依序為:
--   一、行程輸出範本 (agency_export_template)
--   二、報價 / 財務引擎 (currency / margin_setting / quotation / quotation_line)
--   三、四級權限矩陣 + 行程上鎖 (staff_user.is_active / itinerary.is_locked 等)
--   四、計費方式 + 區間價錢 (quotation_line.cost_type / quotation_line_tier / price_tier_template)
--   五、簡易報價編輯頁 (quotation_line.source_item_id 連結行程項目)
-- ============================================================

-- ▼▼▼ 一、行程輸出範本 ▼▼▼

-- 新增：每個旅行社(帳號)可以上傳自己的行程輸出範本 (.docx)
-- 放在 useful_travel_schema.sql 的 export_history 表之後即可

CREATE TABLE agency_export_template (
    AETID INT AUTO_INCREMENT PRIMARY KEY,
    AID INT NOT NULL,                          -- 屬於哪個旅行社帳號
    name VARCHAR(100) NOT NULL,                 -- 範本名稱, 方便同一家旅行社管理多份範本 (e.g. 熱門版/精緻版)
    file_path VARCHAR(255) NOT NULL,            -- 範本 .docx 實體存放路徑 (沿用 ImageStorageService 的儲存方式)
    is_default TINYINT(1) NOT NULL DEFAULT 0,   -- 這家旅行社匯出時預設用哪一份範本
    uploaded_by INT,                            -- staff_user.UID
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_aet_agency FOREIGN KEY (AID) REFERENCES agency(AID),
    CONSTRAINT fk_aet_uploader FOREIGN KEY (uploaded_by) REFERENCES staff_user(UID)
);

CREATE INDEX idx_aet_agency ON agency_export_template(AID);

-- ▼▼▼ 二、報價 / 財務引擎 ▼▼▼

-- ============================================================
-- 報價 / 財務引擎 (需求文件第三章)
-- 放在 useful_travel_schema.sql 之後執行即可
--
-- 設計簡化說明:
--   需求文件把「機票明細」獨立成 flight_fare_detail 表。
--   這裡改成把燃油稅/稅金欄位直接放進 quotation_line
--   (fuel_surcharge / tax_amount)，理由:
--     1. quotation_line 本來就要把每個項目的金額「凍結快照」，
--        機票的稅金本來就是凍結金額的一部分，拆表反而要多一次 JOIN
--        才能算出淨成本。
--     2. component 元件庫仍可以標記 type='flight'，
--        新增報價明細時若選到機票元件，前端把稅金欄位打開即可，
--        邏輯不變、資料表少一張，先求穩定上線。
--   若之後真的需要保留「未進報價單前」的機票報價歷史(比價用)，
--   再把 fuel_surcharge/tax_amount 抽成獨立表也不遲。
-- ============================================================

-- ------------------------------------------------------------
-- 1. 幣別匯率
-- ------------------------------------------------------------
CREATE TABLE currency (
    CID INT AUTO_INCREMENT PRIMARY KEY,
    AID INT DEFAULT NULL,                 -- NULL = 平台共用匯率, 有值 = 該旅行社自訂匯率
    code VARCHAR(10) NOT NULL,            -- TWD / JPY / USD / KRW ...
    name VARCHAR(50),
    rate_to_twd DECIMAL(12,6) NOT NULL DEFAULT 1.000000, -- 1 單位該幣別 = 多少台幣
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (AID) REFERENCES agency(AID)
) ENGINE=InnoDB;

CREATE INDEX idx_currency_code ON currency(code);

-- ------------------------------------------------------------
-- 2. 公司加成規則 (同業%/直售%/退傭%)
-- ------------------------------------------------------------
CREATE TABLE margin_setting (
    MSID INT AUTO_INCREMENT PRIMARY KEY,
    AID INT NOT NULL,
    name VARCHAR(50) NOT NULL,                       -- 規則名稱, e.g. "一般團"、"高端客製團"
    trade_markup_pct DECIMAL(6,2) NOT NULL DEFAULT 0,  -- 同業加成%
    retail_markup_pct DECIMAL(6,2) NOT NULL DEFAULT 0, -- 直售加成%
    rebate_pct DECIMAL(6,2) NOT NULL DEFAULT 0,        -- 退傭%
    is_default TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (AID) REFERENCES agency(AID)
) ENGINE=InnoDB;

CREATE INDEX idx_margin_agency ON margin_setting(AID);

-- ------------------------------------------------------------
-- 3. component 擴充: 幣別 / 是否可退
-- ------------------------------------------------------------
ALTER TABLE component ADD COLUMN currency_code VARCHAR(10) NOT NULL DEFAULT 'TWD';
ALTER TABLE component ADD COLUMN refundable TINYINT(1) NOT NULL DEFAULT 1;

-- ------------------------------------------------------------
-- 4. 報價單主檔 (版本化、可上鎖)
-- ------------------------------------------------------------
CREATE TABLE quotation (
    QID INT AUTO_INCREMENT PRIMARY KEY,
    ITID INT NOT NULL,
    AID INT NOT NULL,
    version INT NOT NULL DEFAULT 1,             -- 同一個 ITID 底下遞增
    MSID INT DEFAULT NULL,                      -- 套用的加成規則 (margin_setting)
    group_size INT NOT NULL DEFAULT 1,          -- 計算 FOC 折抵用的團體人數
    status VARCHAR(20) NOT NULL DEFAULT 'draft',-- draft / locked / confirmed / expired
    note TEXT,
    expires_at DATETIME DEFAULT NULL,           -- 報價單有效期限, 過期自動視為「需重新報價」
    created_by INT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    locked_at DATETIME DEFAULT NULL,
    confirmed_at DATETIME DEFAULT NULL,         -- 客戶簽署/確認後填入, 轉正式訂單的依據
    FOREIGN KEY (ITID) REFERENCES itinerary(ITID) ON DELETE CASCADE,
    FOREIGN KEY (AID) REFERENCES agency(AID),
    FOREIGN KEY (MSID) REFERENCES margin_setting(MSID),
    FOREIGN KEY (created_by) REFERENCES staff_user(UID),
    UNIQUE KEY uk_quotation_version (ITID, version)
) ENGINE=InnoDB;

CREATE INDEX idx_quotation_itinerary ON quotation(ITID);

-- ------------------------------------------------------------
-- 5. 報價單明細 (金額凍結快照)
-- ------------------------------------------------------------
CREATE TABLE quotation_line (
    QLID INT AUTO_INCREMENT PRIMARY KEY,
    QID INT NOT NULL,
    CPID INT DEFAULT NULL,                      -- 來源元件 (component), 自訂項目可為 NULL
    item_name VARCHAR(150) NOT NULL,
    category VARCHAR(20) NOT NULL DEFAULT 'other', -- flight / hotel / meal / attraction / optional / other
    currency_code VARCHAR(10) NOT NULL DEFAULT 'TWD',
    exchange_rate DECIMAL(12,6) NOT NULL DEFAULT 1.000000, -- 下單當下凍結的匯率快照
    unit_price DECIMAL(12,2) NOT NULL DEFAULT 0,   -- 單價 (原幣別)
    quantity INT NOT NULL DEFAULT 1,
    fuel_surcharge DECIMAL(12,2) NOT NULL DEFAULT 0, -- 燃油稅 (機票用, 其他項目留 0)
    tax_amount DECIMAL(12,2) NOT NULL DEFAULT 0,     -- 稅金 (機票用, 其他項目留 0)
    foc_ratio INT NOT NULL DEFAULT 0,              -- 每 N 人折抵 1 位, 0 = 不適用 FOC
    foc_qty INT NOT NULL DEFAULT 0,                -- 依 group_size 換算出的折抵人數快照
    refundable TINYINT(1) NOT NULL DEFAULT 1,
    net_cost DECIMAL(12,2) NOT NULL DEFAULT 0,     -- 淨成本 (台幣, 已扣 FOC)
    trade_price DECIMAL(12,2) NOT NULL DEFAULT 0,  -- 同業價
    retail_price DECIMAL(12,2) NOT NULL DEFAULT 0, -- 直售價
    rebate_amount DECIMAL(12,2) NOT NULL DEFAULT 0,-- 退傭金額
    profit_trade DECIMAL(12,2) NOT NULL DEFAULT 0, -- 利潤(同業)
    profit_retail DECIMAL(12,2) NOT NULL DEFAULT 0,-- 利潤(直售)
    note TEXT,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (QID) REFERENCES quotation(QID) ON DELETE CASCADE,
    FOREIGN KEY (CPID) REFERENCES component(CPID)
) ENGINE=InnoDB;

CREATE INDEX idx_qline_quotation ON quotation_line(QID);

-- ------------------------------------------------------------
-- 6. 預設幣別種子資料 (平台共用, AID = NULL)
-- ------------------------------------------------------------
INSERT INTO currency (AID, code, name, rate_to_twd) VALUES
    (NULL, 'TWD', '新台幣', 1.000000),
    (NULL, 'JPY', '日圓', 0.210000),
    (NULL, 'USD', '美金', 31.500000),
    (NULL, 'KRW', '韓元', 0.023000),
    (NULL, 'CNY', '人民幣', 4.350000);

-- ▼▼▼ 三、權限矩陣 + 行程上鎖 ▼▼▼

-- ============================================================
-- 權限矩陣 (四級) + 行程上鎖 (需求文件第一、二章)
-- 放在 migration_quotation.sql 之後執行即可 (先後順序不影響, 但建議照編號順序執行)
-- ============================================================

-- ------------------------------------------------------------
-- 1. staff_user: 帳號啟用狀態 (「註銷使用者」= 停用, 不是刪除, 避免歷史紀錄斷鏈)
-- ------------------------------------------------------------
ALTER TABLE staff_user ADD COLUMN is_active TINYINT(1) NOT NULL DEFAULT 1;

-- ------------------------------------------------------------
-- 2. 角色資料遷移: 原本 OP/PM/ADMIN 三種, 改成對應需求文件 1.2 權限矩陣的四種角色
--    ADMIN  = 全權限（PM/主管）  : 編輯行程、製作/調整報價、共用槽報價、閱覽 皆可
--    EDITOR = 行程編輯者        : 可編輯行程、可閱覽, 不可碰報價
--    QUOTER = 報價專員          : 可製作/調整報價、可共用槽報價、可閱覽, 不可編輯行程內容
--    VIEWER = 唯讀              : 僅可閱覽
--
--    舊資料沒有「報價專員」這個角色, 舊制度下 OP 本來就能編輯行程,
--    所以遷移時 PM 併入 ADMIN (原本就是主管全權限), OP 併入 EDITOR (維持原本能編輯行程的能力)。
--    未來要把某些 EDITOR 帳號重新指派成 QUOTER 或 VIEWER, 用「使用者權限管理」頁面手動調整即可。
-- ------------------------------------------------------------
UPDATE staff_user SET role = 'ADMIN'  WHERE role = 'PM';
UPDATE staff_user SET role = 'EDITOR' WHERE role = 'OP';

-- ------------------------------------------------------------
-- 3. itinerary: 行程上鎖 (供他人編輯時避免互相覆蓋)
-- ------------------------------------------------------------
ALTER TABLE itinerary ADD COLUMN is_locked TINYINT(1) NOT NULL DEFAULT 0;
ALTER TABLE itinerary ADD COLUMN locked_by INT DEFAULT NULL;   -- staff_user.UID, 誰上的鎖
ALTER TABLE itinerary ADD COLUMN locked_at DATETIME DEFAULT NULL;
ALTER TABLE itinerary ADD CONSTRAINT fk_itinerary_locked_by FOREIGN KEY (locked_by) REFERENCES staff_user(UID);

-- ▼▼▼ 四、計費方式 + 區間價錢 ▼▼▼

-- ============================================================
-- 計費方式 (按人頭 / 全團固定) + 區間價錢 (依人數級距定價)
-- 放在 migration_combined.sql 之後執行即可
-- ============================================================

-- ------------------------------------------------------------
-- 1. quotation_line: 計費方式
--    PER_PAX      = 按人頭計費 (單價 x 計費人數, 門票/餐食這種)
--    FIXED_GROUP  = 全團固定一口價 (遊覽車/導遊/領隊費這種, 不受人數影響, 只受「數量」影響, e.g. 兩台車)
-- ------------------------------------------------------------
ALTER TABLE quotation_line ADD COLUMN cost_type VARCHAR(20) NOT NULL DEFAULT 'PER_PAX';

-- ------------------------------------------------------------
-- 2. 區間價錢: 掛在單一報價項目底下 (不是掛在整張報價單), 因為遊覽車跟導遊費的級距切法通常不一樣
--    每一列代表一個級距: [min_qty, max_qty] 對應一個價錢, max_qty 留空 = 開放區間 (例如「32人以上」)
-- ------------------------------------------------------------
CREATE TABLE quotation_line_tier (
    QLTID INT AUTO_INCREMENT PRIMARY KEY,
    QLID INT NOT NULL,
    min_qty INT NOT NULL,
    max_qty INT DEFAULT NULL,
    price DECIMAL(12,2) NOT NULL,      -- 這個級距對應的價錢 (原幣別, 換算方式跟這筆項目本身的 currency_code/exchange_rate 一致)
    sort_order INT NOT NULL DEFAULT 0,
    FOREIGN KEY (QLID) REFERENCES quotation_line(QLID) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE INDEX idx_qlt_line ON quotation_line_tier(QLID);

-- ------------------------------------------------------------
-- 3. 級距範本: 把編好的一組級距存起來, 下次開新報價單可以直接套用, 不用每次重新輸入
-- ------------------------------------------------------------
CREATE TABLE price_tier_template (
    PTTID INT AUTO_INCREMENT PRIMARY KEY,
    AID INT NOT NULL,
    name VARCHAR(100) NOT NULL,        -- e.g. 「北海道遊覽車級距」
    created_by INT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (AID) REFERENCES agency(AID),
    FOREIGN KEY (created_by) REFERENCES staff_user(UID)
) ENGINE=InnoDB;

CREATE INDEX idx_ptt_agency ON price_tier_template(AID);

CREATE TABLE price_tier_template_row (
    PTTRID INT AUTO_INCREMENT PRIMARY KEY,
    PTTID INT NOT NULL,
    min_qty INT NOT NULL,
    max_qty INT DEFAULT NULL,
    price DECIMAL(12,2) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    FOREIGN KEY (PTTID) REFERENCES price_tier_template(PTTID) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE INDEX idx_pttr_template ON price_tier_template_row(PTTID);

-- ▼▼▼ 五、簡易報價編輯頁 (行程項目連結) ▼▼▼

-- ============================================================
-- 簡易報價編輯頁: 讓報價項目可以連結到行程裡的某個景點/餐廳/飯店項目
-- 放在 migration_price_tier.sql 之後執行即可
--
-- 設計說明: 簡易報價頁跟正式報價頁 (/quotation/{qid}) 讀寫的是同一份 quotation_line 資料,
-- 不是另外開一套。差別只在於簡易頁面多了 source_item_id 這個連結——
-- 填價錢時如果這個項目已經連過一筆報價明細, 就更新原本那筆, 不會重複新增。
-- source_item_id 是 NULL 的那些明細 (雜費/機票/自訂項目) 則兩邊頁面共用同一套「無來源」邏輯,
-- 不需要額外欄位。
-- ============================================================

ALTER TABLE quotation_line ADD COLUMN source_item_id INT DEFAULT NULL;
ALTER TABLE quotation_line ADD CONSTRAINT fk_qline_source_item
    FOREIGN KEY (source_item_id) REFERENCES itinerary_item(IIID) ON DELETE SET NULL;
-- 行程項目被刪除時只解除連結、不連帶刪除報價明細, 避免已經填好的成本資料無聲消失

CREATE INDEX idx_qline_source_item ON quotation_line(source_item_id);
