-- ============================================================
-- 把 POI 資料庫裡舊資料的中文 category 統一轉成跟畫面一致的英文值
-- 請務必在 migration_poi_airport_category.sql 執行「之後」才執行這一份
-- (那份 migration 會先把 category='交通' 裡屬於機場的部分挑出來改成 'airport',
--  這份再把剩下的 '交通' 轉成 'transport'; 順序顛倒的話機場會被錯誤地轉成 'transport')。
--
-- 背景:
--   檢查使用者上傳的完整資料庫備份後發現, POI 資料庫裡「景點/餐廳/飯店/購物」這幾類舊資料
--   (約2900筆) 的 category 存的是中文 ('景點'/'餐廳'/'飯店'/'購物'), 但畫面上的類型篩選
--   下拉選單 (poi/list.html) 跟新增/編輯表單 (poi/new.html、poi/edit.html) 送出的 category 值
--   是英文 ('attraction'/'restaurant'/'hotel'/'rest_stop')。兩邊值對不上, 導致「景點資料庫」
--   列表頁的「類型」篩選對這批舊資料完全篩不出東西, 只有透過畫面新增的資料才會是英文、篩選才會生效。
--
--   同時發現 ItineraryService.java 裡有兩個地方 (AI 安排行程時判斷候選景點裡有沒有餐廳/飯店、
--   把行程項目寫回 POI 資料庫時決定新資料的 category) 也是用中文字面比對/產生, 這次已經一併
--   改成跟畫面一致的英文, 詳見對應的程式碼 patch。
--
-- 這份 migration 純粹是「重新命名既有資料的 category 值」, 不會新增/刪除/搬動任何一筆資料,
-- 也不影響 name/original_name/country/city/座標等其他欄位。
--
-- 補充: '交通' 裡不是機場的部分 (火車站等其他交通工具, 約73筆) 這裡轉成新的 'transport' 分類;
-- '購物' (約5筆) 轉成新的 'shopping' 分類。畫面的類型下拉選單、清單頁標籤樣式也一併補上這兩個
-- 新分類的選項, 詳見對應的程式碼 patch。
-- ============================================================

UPDATE poi SET category = 'attraction' WHERE category = '景點';
UPDATE poi SET category = 'restaurant' WHERE category = '餐廳';
UPDATE poi SET category = 'hotel' WHERE category = '飯店';
UPDATE poi SET category = 'transport' WHERE category = '交通';
UPDATE poi SET category = 'shopping' WHERE category = '購物';
