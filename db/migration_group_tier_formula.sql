-- ============================================================
-- 補回遺漏的 migration:「整團人數級距報價結果」雜項／NP／團費成本計算公式 + 級距勾選欄位
--
-- 背景 (2026-08-25 稽核發現): QuotationGroupTier.java／QuotationLine.java／Quotation.java 這三個
-- entity、以及 QuotationService.java 的 applyGroupTierFormulaSettings()／recalculateGroupTiers()
-- 從很早的改版開始就已經在讀寫下面這些欄位, 但這個 db/ 資料夾裡從來沒有真正建立過它們的 ALTER TABLE
-- migration —— 這就是為什麼「基本報價／雜項／NP／團費成本」的計算公式看起來「一直沒做」、以及「選擇公式
-- 再填基本報價公式會整個當掉內容消失」: 只要程式一去查詢 quotation_line 或 quotation_group_tier 這兩張表
-- (幾乎每個報價單操作都會查), Hibernate 都會因為資料庫實際上沒有這些欄位而丟例外, 沒有被保護到的呼叫路徑
-- (例如 recalculateAll() 一開始就要整批查 quotation_line) 就會讓整個請求失敗、畫面看起來像當掉。
--
-- 這份 migration 全部用 ADD COLUMN IF NOT EXISTS, 可以放心地在任何環境重複執行 (MySQL 8.0.29+ 支援語法);
-- 如果你的資料庫版本比較舊不支援 IF NOT EXISTS, 麻煩告訴我, 我再另外準備一份用「先查再視情況執行」的版本。
-- ============================================================

-- 1. quotation: 「雜項的固定成本除以級距的」代表人數模式 (LOWER=下限 / AVERAGE=平均 / UPPER=上限)
ALTER TABLE quotation
    ADD COLUMN IF NOT EXISTS group_tier_headcount_mode VARCHAR(10) NOT NULL DEFAULT 'LOWER';

-- 2. quotation_line: 「級距」勾選欄位 (勾選後才會出現在「區間價錢管理」卡片, 可以設定人數對應價錢)
ALTER TABLE quotation_line
    ADD COLUMN IF NOT EXISTS tier_managed TINYINT(1) NOT NULL DEFAULT 0;

-- 3. quotation_group_tier: 雜項（全團固定費用）／NP／團費成本 這一整組計算公式欄位 + 試算結果快照
ALTER TABLE quotation_group_tier
    ADD COLUMN IF NOT EXISTS currency VARCHAR(10) NOT NULL DEFAULT 'TWD',
    ADD COLUMN IF NOT EXISTS misc_value DECIMAL(14,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS misc_value_twd DECIMAL(14,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS np_formula VARCHAR(500) NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS np_result_twd DECIMAL(14,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS team_formula VARCHAR(500) NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS team_result_twd DECIMAL(14,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS variable_cost_per_person_twd DECIMAL(14,2) NOT NULL DEFAULT 0;

-- 4. margin_setting: 補上「①基本報價公式」欄位 —— 使用者要求「已儲存的」計算公式規則要能連基本報價
--    一起存, 不要只管同業/直售/退傭三層。跟同一張表既有的 trade_formula/retail_formula/rebate_formula
--    一樣是 nullable VARCHAR(500): 沒填代表這組規則不管基本報價這一層, 套用時繼續沿用那張報價單自己的
--    basic_markup_mode/basic_markup_value 設定 (見 MarginSetting.java 欄位註解)。
ALTER TABLE margin_setting
    ADD COLUMN IF NOT EXISTS basic_formula VARCHAR(500) DEFAULT NULL;

-- 5. margin_setting: 「整團人數級距報價結果」卡片的 NP／團費成本計算式, 一樣可以存進「計算公式管理」。
--    跟 quotation_group_tier 的 np_formula/team_formula 欄位同名但完全獨立 (不同表), 空白代表這組規則
--    不管 NP/團費成本這一層, 套用時繼續沿用那張報價單「整團人數級距報價結果」卡片自己填的 (見
--    MarginSetting.java 欄位註解)。
ALTER TABLE margin_setting
    ADD COLUMN IF NOT EXISTS np_formula VARCHAR(500) DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS team_formula VARCHAR(500) DEFAULT NULL;

-- 6. quotation: NP／團費成本這組獨立的「已儲存的／自填」切換, 跟 formula_mode/MSID (管①②③④) 完全分開
--    (使用者要求兩組要能各自分開選規則), 語意/預設值/容錯規則比照 migration_formula_pricing.sql 當時建立
--    formula_mode 的做法: tier_formula_mode 預設 'custom' (不是 'preset', 避免舊資料被誤判成已經套用了
--    某組實際上沒選過的規則——這點特意跟 formula_mode 預設 'preset' 不同, 因為這是全新欄位, 不存在「舊資料」
--    這個問題, 用 'custom' 當預設更符合「沒設定過就是自己填」的直覺)。
ALTER TABLE quotation
    ADD COLUMN IF NOT EXISTS tier_formula_mode VARCHAR(10) NOT NULL DEFAULT 'custom',
    ADD COLUMN IF NOT EXISTS tier_MSID INT NULL;
