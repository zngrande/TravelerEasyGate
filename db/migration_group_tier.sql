-- ============================================================
-- 整團人數級距報價 (跟 migration_price_tier.sql 的「單一項目區間價錢」不一樣層級)
-- 放在 migration_price_tier.sql 之後執行即可
--
-- 設計說明:
--   quotation_line_tier (migration_price_tier.sql) 是「成本端」——
--     單一項目 (遊覽車/導遊費...) 本身的價錢會因人數不同而不同。
--   quotation_group_tier (這份) 是「賣價端」——
--     把整張報價單目前所有項目的成本 (含上面那些會隨人數變動的成本),
--     在某個人數級距下加總、除以人數、套加成規則, 算出「這個級距每人賣多少錢」。
--   兩者疊在一起用: group tier 的試算會把 line tier 的效果也算進去。
--
--   級距內的固定成本 (FIXED_GROUP 項目) 分攤人數, 用級距下限 (min_qty) 當代表人數計算
--   (保守作法: 就算真的只湊到下限人數, 固定成本也要能 cover 得住)。
--
--   下面這些計算欄位都是「系統自動算出來的快照」, 不開放手動輸入:
--   每次報價單的成本/加成規則/人數有變動時, service 層會重新算過並存回這幾欄,
--   跟 quotation_line 的 net_cost/trade_price/... 是同一套「凍結快照」精神。
-- ============================================================
CREATE TABLE quotation_group_tier (
    QGTID INT AUTO_INCREMENT PRIMARY KEY,
    QID INT NOT NULL,
    min_qty INT NOT NULL,
    max_qty INT DEFAULT NULL,              -- 留空 = 開放區間 (這個人數以上都適用)
    sort_order INT NOT NULL DEFAULT 0,

    -- 以下皆為自動計算快照 (用 min_qty 當代表人數分攤固定成本後算出來的結果)
    total_net_cost DECIMAL(14,2) NOT NULL DEFAULT 0,      -- 這個級距的總成本 (以代表人數計)
    net_cost_per_pax DECIMAL(14,2) NOT NULL DEFAULT 0,    -- 每人成本
    trade_price_per_pax DECIMAL(14,2) NOT NULL DEFAULT 0, -- 同業價 (每人)
    retail_price_per_pax DECIMAL(14,2) NOT NULL DEFAULT 0,-- 直售價 (每人)
    margin_rate_pct DECIMAL(6,2) NOT NULL DEFAULT 0,      -- 毛利率% = (直售價-成本)/直售價

    FOREIGN KEY (QID) REFERENCES quotation(QID) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE INDEX idx_qgt_quotation ON quotation_group_tier(QID);
