-- ============================================================
-- 「加成規則」→「計算公式」升級 migration
-- 請在 migration_quotation.sql (或 migration_combined.sql) 執行過之後再跑這一份。
--
-- 背景: 原本 margin_setting 只能填死板的「同業加成%／直售加成%／退傭%」，
-- 現在改成可以直接寫運算式 (例如 "{NET_COST} * 1.15 + 2000")，每家旅行社／
-- 每個同業對象都可以有自己的算法，不用被綁死在單純乘一個百分比上。
--
-- 相容性設計: 這裡是「新增欄位」而不是砍掉重練，trade_markup_pct / retail_markup_pct /
-- rebate_pct 這三個舊欄位保留不動。新公式欄位是 nullable，舊資料完全不用搬移 —
-- QuotationService 算價錢時，只要公式欄位是空的，就會自動 fallback 回舊的 % 算法。
-- ============================================================

-- ------------------------------------------------------------
-- 1. 公司加成規則 (margin_setting): 新增三個公式欄位
-- ------------------------------------------------------------
ALTER TABLE margin_setting ADD COLUMN trade_formula VARCHAR(500) DEFAULT NULL;   -- 同業價公式, 可用變數 {NET_COST} {GROUP_SIZE}
ALTER TABLE margin_setting ADD COLUMN retail_formula VARCHAR(500) DEFAULT NULL;  -- 直售價公式, 可用變數 {NET_COST} {GROUP_SIZE} {TRADE_PRICE}
ALTER TABLE margin_setting ADD COLUMN rebate_formula VARCHAR(500) DEFAULT NULL;  -- 退傭金額公式, 可用變數 {NET_COST} {GROUP_SIZE} {TRADE_PRICE}

-- ------------------------------------------------------------
-- 2. 報價單主檔 (quotation): 新增「自填公式」模式用的欄位
--    formula_mode = 'preset' (預設, 套用 MSID 指到的規則) 或 'custom' (這張報價單自己填的公式)
-- ------------------------------------------------------------
ALTER TABLE quotation ADD COLUMN formula_mode VARCHAR(10) NOT NULL DEFAULT 'preset';
ALTER TABLE quotation ADD COLUMN custom_trade_formula VARCHAR(500) DEFAULT NULL;
ALTER TABLE quotation ADD COLUMN custom_retail_formula VARCHAR(500) DEFAULT NULL;
ALTER TABLE quotation ADD COLUMN custom_rebate_formula VARCHAR(500) DEFAULT NULL;
