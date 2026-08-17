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
