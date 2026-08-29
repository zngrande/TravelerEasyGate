-- Patch 38: 元件庫加入「按人頭 / 全團固定一口價」計費方式, 跟報價單項目 (quotation_line.cost_type)
-- 用同一套值，拉進報價單時直接沿用這裡填的計費方式當預設值 (使用者還是可以在報價單裡改)。
ALTER TABLE component ADD COLUMN cost_type VARCHAR(20) NOT NULL DEFAULT 'PER_PAX' AFTER default_price;
