-- 建立行程頁面新增「行程重點資訊」: 去程/回程班機資訊 + 行程說明
-- 去程/回程班機資訊本身不需要新的資料表欄位 —— 直接轉成 itinerary_item 資料表既有的
-- item_type='transport' 項目 (from_location/to_location/transport_method/start_time/end_time 都已經存在,
-- 是更早之前「交通類別項目」那個功能建的欄位), 插入第一天第一筆 / 最後一天最後一筆。
-- 這裡只需要新增一個欄位: itinerary.description (行程說明, 選填, 建立時填寫, 「AI 安排行程」時也會
-- 一併當作額外的規劃參考文字丟給 AI)。
ALTER TABLE itinerary ADD COLUMN description TEXT NULL;
