-- ============================================================
-- 「計算公式管理」預設規則拆分成兩組獨立預設
--
-- 背景: 使用者反映「報價定價規則」跟「NP／團費成本規則」應該要能各自設定一組預設
-- (建立新報價單時, 基本報價這組跟 NP/團費成本這組要能分開自動帶入不同的預設規則),
-- 但原本 margin_setting 只有一個 is_default 欄位, 整間旅行社同一時間只能有一組
-- 預設規則, 而且這一組預設不管是報價定價還是 NP/團費成本都是同一組, 沒辦法「報價定價
-- 用 A 規則當預設, NP/團費成本用 B 規則當預設」這樣分開設定。
--
-- 2026-09-03 補記: 這支變動原本寫成 db/migration_margin_setting_split_default.sql
-- (放在專案根目錄的舊式手動 SQL 資料夾), 但那個時候程式其實已經改用 Flyway 自動管理
-- 資料庫結構 (見本資料夾的 README.md)——結果這支檔案從沒被放進 Flyway 真正會掃描、
-- 自動套用的 src/main/resources/db/migration/ 資料夾, 變成「程式碼已經在用
-- default_pricing/default_tier 這兩個新欄位, 但沒有人手動去資料庫补跑過這支 SQL, 欄位
-- 根本不存在」——這就是「正式報價單一打開就出錯」的真正原因 (Hibernate 送出的 SQL 查
-- margin_setting.default_pricing / default_tier, 資料庫回報 Unknown column)。現在補成
-- 正式的 Flyway migration, 程式一啟動就會自動套用, 不用再手動執行 SQL。
--
-- 這裡新增 default_pricing / default_tier 兩個獨立欄位取代原本的 is_default,
-- 並且把舊資料的 is_default 依照原本程式邏輯 (MarginSettingController#create /
-- QuotationService#createQuotation 舊版) 正確地搬進這兩個新欄位: 原本「不是純
-- NP/團費成本規則」的預設規則搬進 default_pricing, 「有填 NP 或團費成本公式」的
-- 預設規則搬進 default_tier (混合規則兩邊都會搬到, 跟舊行為一致)。
--
-- is_default 這個舊欄位保留不動 (不刪除), 只是程式碼之後不會再讀寫它, 純粹避免
-- 舊資料被砍掉、也避免跟其他還沒套用這份 migration 的環境對不起來。
--
-- 註: db/migration_margin_setting_split_default.sql 那份舊檔案已經沒有作用, 保留在原地
-- 只是當作歷史紀錄, 之後有新的資料庫變更一律照 README.md 說的新增 V{數字}__ 檔案到這個
-- 資料夾, 不要再回到手動執行 SQL 的舊做法。
--
-- 這裡刻意用 ADD COLUMN IF NOT EXISTS (跟 db/before_flyway_catchup.sql 同一個寫法,
-- 需要 MySQL 8.0.29+): 如果你的環境剛好曾經手動執行過那支舊檔案、欄位其實已經加過了,
-- 這樣寫 Flyway 首次啟動套用這支 migration 時才不會因為「Duplicate column」直接啟動失敗。
-- 如果你的 MySQL 版本比較舊、這支跑起來報語法錯誤, 請先手動用
-- `SHOW COLUMNS FROM margin_setting LIKE 'default_pricing'` 確認欄位是否已存在,
-- 已存在的話把下面兩行 ADD COLUMN 刪掉再重新啟動即可。
-- ============================================================

ALTER TABLE margin_setting ADD COLUMN IF NOT EXISTS default_pricing TINYINT(1) NOT NULL DEFAULT 0;
ALTER TABLE margin_setting ADD COLUMN IF NOT EXISTS default_tier TINYINT(1) NOT NULL DEFAULT 0;

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
