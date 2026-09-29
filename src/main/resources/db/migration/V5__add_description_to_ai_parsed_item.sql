-- AI 解析暫存項目新增「簡介/介紹說明」欄位, 跟既有的 note (原文裡一句話的補充備註, 例如
-- 「含早餐」「注意事項」) 分開存: description 是原文裡針對這個地點寫的完整介紹文字
-- (例如企劃書裡一整段景點簡介)。確認轉成正式行程時會存進 itinerary_item.ai_description
-- (見 V4__add_ai_description_to_itinerary_item.sql), 讓匯出企劃書/行程編輯畫面可以顯示。
ALTER TABLE ai_parsed_item ADD COLUMN description TEXT NULL;
