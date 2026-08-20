-- ============================================================
-- 退傭也改成跟基本/同業/直售加成一樣, 可以選「依%數計算」或「自填金額」。
--   mode=PERCENT: 退傭金額 = 同業價 × rebate_pct%
--   mode=AMOUNT : 退傭金額 = rebate_pct 這個固定金額 (欄位名稱沿用 rebate_pct, 當作數值用)
-- ============================================================
ALTER TABLE quotation ADD COLUMN rebate_mode VARCHAR(10) NOT NULL DEFAULT 'PERCENT';
