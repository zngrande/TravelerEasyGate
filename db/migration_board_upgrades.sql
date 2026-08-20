-- ============================================================
-- 行程排版看板功能升級 (2026-08):
--   1. 補上 show_on_map 欄位 —— 這欄位其實程式早就在用 (entity/controller/前端都有),
--      但翻遍所有 migration 檔案都找不到當初新增它的 ALTER TABLE, 推測是當初直接手動
--      改資料庫、忘記寫進 migration。
--   2. 新增「交通」項目 (item_type = 'transport') 專用欄位: 起始點/起始地址/目的地/
--      目的地地址/交通工具/通勤時間。一般景點/餐廳/住宿不會用到這些欄位, 維持 NULL 即可。
--
-- 這份檔案改用 ADD COLUMN IF NOT EXISTS, 不管你的資料庫先前是不是已經手動加過 show_on_map,
-- 都可以整份直接執行、也可以重複執行, 不會因為某一欄位已存在就整份中斷 (MySQL 用戶端預設遇到
-- 錯誤就會停在那一行、後面的指令都不會執行, 這是先前那個版本會漏掉交通欄位的原因)。
--
-- 需要 MySQL 8.0.29 以上才支援 IF NOT EXISTS 這個寫法; 如果你的 MySQL 版本比較舊、執行這份
-- 檔案出現語法錯誤, 改把每一行的 "IF NOT EXISTS" 拿掉即可 (逐行執行, 已存在的欄位手動跳過那一行)。
-- ============================================================

ALTER TABLE itinerary_item ADD COLUMN IF NOT EXISTS show_on_map TINYINT(1) NOT NULL DEFAULT 1;

ALTER TABLE itinerary_item ADD COLUMN IF NOT EXISTS from_location VARCHAR(150) DEFAULT NULL;   -- 起始點名稱
ALTER TABLE itinerary_item ADD COLUMN IF NOT EXISTS from_address VARCHAR(300) DEFAULT NULL;    -- 起始地址 (沒填會自動查詢帶入)
ALTER TABLE itinerary_item ADD COLUMN IF NOT EXISTS to_location VARCHAR(150) DEFAULT NULL;     -- 目的地名稱
ALTER TABLE itinerary_item ADD COLUMN IF NOT EXISTS to_address VARCHAR(300) DEFAULT NULL;      -- 目的地地址 (沒填會自動查詢帶入)
ALTER TABLE itinerary_item ADD COLUMN IF NOT EXISTS transport_method VARCHAR(30) DEFAULT NULL; -- 交通工具 (高鐵/飛機/遊覽車...)
ALTER TABLE itinerary_item ADD COLUMN IF NOT EXISTS commute_duration VARCHAR(50) DEFAULT NULL; -- 通勤時間 (自由文字)

