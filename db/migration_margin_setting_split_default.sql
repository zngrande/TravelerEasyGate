-- ============================================================
-- 「計算公式管理」預設規則拆分成兩組獨立預設 migration
--
-- 背景: 使用者反映「報價定價規則」跟「NP／團費成本規則」應該要能各自設定一組預設
-- (建立新報價單時, 基本報價這組跟 NP/團費成本這組要能分開自動帶入不同的預設規則),
-- 但原本 margin_setting 只有一個 is_default 欄位, 整間旅行社同一時間只能有一組
-- 預設規則, 而且這一組預設不管是報價定價還是 NP/團費成本都是同一組, 沒辦法「報價定價
-- 用 A 規則當預設, NP/團費成本用 B 規則當預設」這樣分開設定。
--
-- 這裡新增 default_pricing / default_tier 兩個獨立欄位取代原本的 is_default,
-- 並且把舊資料的 is_default 依照原本程式邏輯 (MarginSettingController#create /
-- QuotationService#createQuotation 舊版) 正確地搬進這兩個新欄位: 原本「不是純
-- NP/團費成本規則」的預設規則搬進 default_pricing, 「有填 NP 或團費成本公式」的
-- 預設規則搬進 default_tier (混合規則兩邊都會搬到, 跟舊行為一致)。
--
-- is_default 這個舊欄位保留不動 (不刪除), 只是程式碼之後不會再讀寫它, 純粹避免
-- 舊資料被砍掉、也避免跟其他還沒套用這份 migration 的環境對不起來。
-- ============================================================

ALTER TABLE margin_setting ADD COLUMN default_pricing TINYINT(1) NOT NULL DEFAULT 0;
ALTER TABLE margin_setting ADD COLUMN default_tier TINYINT(1) NOT NULL DEFAULT 0;

UPDATE margin_setting
SET default_pricing = CASE
        WHEN is_default = 1
             AND NOT (
                 (basic_formula IS NULL AND trade_formula IS NULL AND retail_formula IS NULL AND rebate_formula IS NULL)
                 AND (np_formula IS NOT NULL OR team_formula IS NOT NULL)
             )
        THEN 1 ELSE 0
    END,
    default_tier = CASE
        WHEN is_default = 1 AND (np_formula IS NOT NULL OR team_formula IS NOT NULL)
        THEN 1 ELSE 0
    END;
