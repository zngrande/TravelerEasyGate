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
