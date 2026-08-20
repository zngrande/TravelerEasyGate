-- ============================================================
-- 退傭%改成每張報價單自己獨立填 (不再依賴「加成規則」範本), 邏輯上跟同業/直售加成放在一起。
-- 這次改動之後, 新建報價單時「同業加成/直售加成/退傭%」這三個數字改成:
--   有上一版報價單 (同一個行程) → 直接帶入上一版的三個數字, 方便微調
--   沒有上一版 (第一次建立)     → 全部從 0 開始
-- 不再從「加成規則」(margin_setting) 帶入初始值, 「加成規則」這個範本選擇從報價流程中移除
-- (margin_setting 資料表本身沒有刪除, 只是報價單不再依賴它)。
-- ============================================================
ALTER TABLE quotation ADD COLUMN rebate_pct DECIMAL(6,2) NOT NULL DEFAULT 0;

-- 既有報價單的退傭%, 用它原本掛的加成規則範本帶進來 (沒有掛範本的維持 0)
UPDATE quotation q
JOIN margin_setting ms ON ms.MSID = q.MSID
SET q.rebate_pct = ms.rebate_pct
WHERE q.MSID IS NOT NULL;
