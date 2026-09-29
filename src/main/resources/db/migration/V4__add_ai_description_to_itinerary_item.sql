-- 使用者要求: AI 解析上傳的行程文件時, 如果原文裡有針對某個地點寫的簡介/介紹文字,
-- 也要一併帶進行程, 而且如果跟公司景點資料庫既有的介紹說明重疊, 要優先顯示 AI 解析出來的版本。
-- 這裡新增的 ai_description 只是「暫存」在這個行程項目自己身上, 不會反過來覆寫共用的
-- poi.description —— 只有使用者之後在行程編輯畫面手動存檔修改介紹說明時, 才會真的寫回
-- poi 資料表 (見 PoiService.updateDescription), 屆時這個暫存欄位也會被清空, 之後改以
-- 資料庫版本為準。
ALTER TABLE itinerary_item ADD COLUMN ai_description TEXT NULL;
