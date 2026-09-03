-- ============================================================
-- 啟用 Flyway 前的「補跑」腳本
-- ============================================================
--
-- 目的: 這次改用 Flyway 自動管理資料庫結構之後, Flyway 會把「你現在的資料庫」直接當成
-- 「已經是最新的了」(這叫做 baseline, 詳見 db/migration/README.md)。如果你之前
-- db/migration_xxx.sql 這些散落的檔案裡, 剛好有幾支還沒手動跑過, Flyway 並不會發現、
-- 也不會幫你補跑——它會誤以為那些欄位本來就存在。這支腳本就是要在啟用 Flyway 的「前一刻」,
-- 先確保這幾支最近新增、比較可能還沒跑過的檔案都真的套用了。
--
-- 這支腳本每一段都用 MySQL 8.0.29+ 支援的「IF NOT EXISTS」寫法, 已經跑過的部分會直接跳過、
-- 不會報錯, 所以整支重複執行幾次也不會壞掉 (這叫「幂等 idempotent」), 你可以放心整支貼上去跑,
-- 不用自己一段一段判斷「這個我是不是已經跑過了」。
--
-- 如果你的 MySQL 版本比 8.0.29 舊、跑這支會出現語法錯誤: 請改成一段一段手動檢查
-- (SHOW COLUMNS FROM 資料表 LIKE '欄位名') 再決定要不要跑, 或直接升級 MySQL 版本
-- (8.0.29 是 2022 年的版本, 正常情況不太會遇到比這更舊的)。
--
-- 執行方式: 用 MySQL 用戶端連進你的資料庫 (改名成 traveler_easy_gate 的話就連那個),
-- 把整支貼上去執行一次即可。
-- ============================================================

-- 1) 行程輸出範本支援三種類型 (客戶 Word / 同業 Word / 同業 Excel)
ALTER TABLE agency_export_template
  ADD COLUMN IF NOT EXISTS template_type VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER' AFTER name;
UPDATE agency_export_template SET template_type = 'CUSTOMER' WHERE template_type IS NULL OR template_type = '';

-- 2) 行程項目支援「航班/車次編號」(例如 CI100)
ALTER TABLE itinerary_item
  ADD COLUMN IF NOT EXISTS transport_number VARCHAR(50) NULL AFTER transport_method;

-- 3) AI 解析暫存項目支援 item_type='transport' (航班/高鐵/包車等交通資訊)
ALTER TABLE ai_parsed_item
  ADD COLUMN IF NOT EXISTS from_location VARCHAR(100) NULL AFTER note,
  ADD COLUMN IF NOT EXISTS to_location VARCHAR(100) NULL AFTER from_location,
  ADD COLUMN IF NOT EXISTS transport_method VARCHAR(50) NULL AFTER to_location,
  ADD COLUMN IF NOT EXISTS transport_number VARCHAR(50) NULL AFTER transport_method,
  ADD COLUMN IF NOT EXISTS departure_time TIME NULL AFTER transport_number,
  ADD COLUMN IF NOT EXISTS arrival_time TIME NULL AFTER departure_time;

-- 4) 員工角色改成可以多選 (逗號分隔字串, 例如 "EDITOR,QUOTER"), 原本 VARCHAR(20) 放不下
ALTER TABLE staff_user
  MODIFY COLUMN role VARCHAR(60) DEFAULT 'VIEWER';

-- ------------------------------------------------------------
-- 確認腳本: 跑完上面之後, 用這幾行檢查看看有沒有跑成功
-- (每一行都應該回傳 1 筆結果、Field 是你剛剛加的那個欄位名稱, 沒有結果代表沒加成功)
-- ------------------------------------------------------------
-- SHOW COLUMNS FROM agency_export_template LIKE 'template_type';
-- SHOW COLUMNS FROM itinerary_item LIKE 'transport_number';
-- SHOW COLUMNS FROM ai_parsed_item LIKE 'transport_number';
-- SHOW COLUMNS FROM staff_user LIKE 'role';
