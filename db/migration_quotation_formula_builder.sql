-- ============================================================
-- 報價單「基本報價／同業／直售加成與退傭設定」改成公式建構器
-- 請在 migration_formula_pricing.sql 執行過之後再跑這一份。
--
-- 背景: migration_formula_pricing.sql 當初已經幫 quotation 加了 custom_trade_formula /
-- custom_retail_formula / custom_rebate_formula 三個公式欄位, 但一直沒有接上畫面跟計算邏輯。
-- 這次把「基本報價／同業／直售加成與退傭設定」卡片改成跟 margin-setting (計算公式管理) 同一套
-- 拖拉式公式建構器, 正式把這三個欄位接上, 並補上當初沒有的「基本報價」這一層。
--
-- 相容性設計: 純新增欄位, 不影響任何舊資料。custom_basic_formula 是 nullable,
-- QuotationService 算價錢時, 只要某一層的公式欄位是空的, 就會自動 fallback 回舊制
-- basic_markup_mode/value 等 %/自填金額算法, 兩邊並存不衝突。
-- ============================================================

ALTER TABLE quotation ADD COLUMN custom_basic_formula VARCHAR(500) DEFAULT NULL AFTER formula_mode;
