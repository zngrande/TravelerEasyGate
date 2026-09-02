-- 新增: 「行程輸出範本」支援兩種類型
--   CUSTOMER → 給客戶看的企劃書範本 (.docx, Word), 原本就有的功能
--   AGENCY   → 給同業 (B2B 夥伴) 看的報價單範本 (.xlsx, Excel), 這次新增
-- 舊資料一律預設成 CUSTOMER, 不影響原本已經上傳的 .docx 範本跟「預設範本」設定

ALTER TABLE agency_export_template
  ADD COLUMN template_type VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER' AFTER name;

UPDATE agency_export_template SET template_type = 'CUSTOMER' WHERE template_type IS NULL OR template_type = '';

-- 「預設範本」原本是整個帳號只有一份 is_default=1, 現在改成每個類型各自可以有一份預設
-- (例如客戶版型跟同業版型可以各自設定不同的預設範本), 這裡不需要额外欄位,
-- 沿用原本的 is_default, 程式邏輯改成「同類型底下才互斥」即可, 不用動到資料。

-- 後續追加: 又新增了第三種類型 AGENCY_WORD (給同業看的企劃書範本, .docx, Word),
-- 跟本來就有的 AGENCY (給同業看的報價單範本, .xlsx, Excel) 是不同東西, 分開管理各自的預設範本。
-- template_type 欄位本來就是自由文字的 VARCHAR(20), 不需要再多一次 ALTER TABLE,
-- 直接讓程式碼寫入 'AGENCY_WORD' 這個新值即可, 舊資料不受影響。
