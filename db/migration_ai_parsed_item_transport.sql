-- 新增: AI 解析暫存項目 (ai_parsed_item) 支援 item_type='transport' (航班/高鐵/包車等交通資訊)。
-- 原本 AI 解析會完全略過這類資訊 (見 AiParseService 系統提示詞舊版), 這次改成會解析出來、
-- 存進這幾個新欄位, 使用者在 review 頁面確認後, confirmImport() 轉正式行程時會依這些欄位組成
-- 一筆 itinerary_item (item_type=transport), 顯示方式跟「建立新行程」手動填的去程/回程班機一致
-- (ItineraryService.buildFlightLabel: 有填航班/車次編號就顯示「編號 出發地→目的地」)。
-- 全部欄位皆為 NULL 可, 不影響其他 item_type (attraction/meal/hotel/highlight) 的既有資料。
ALTER TABLE ai_parsed_item
  ADD COLUMN from_location VARCHAR(100) NULL AFTER note,
  ADD COLUMN to_location VARCHAR(100) NULL AFTER from_location,
  ADD COLUMN transport_method VARCHAR(50) NULL AFTER to_location,
  ADD COLUMN transport_number VARCHAR(50) NULL AFTER transport_method,
  ADD COLUMN departure_time TIME NULL AFTER transport_number,
  ADD COLUMN arrival_time TIME NULL AFTER departure_time;
