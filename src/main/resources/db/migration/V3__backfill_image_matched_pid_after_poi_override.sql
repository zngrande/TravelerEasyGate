-- ============================================================
-- 補跑一次性資料修正:「景點資料庫編輯頁的『綁定圖片』顯示尚未綁定,
-- 但圖片資源庫明明顯示已綁定景點」
--
-- 背景: PoiService.overrideSharedPoi() 讓旅行社編輯共用庫景點時, 會複製一份專屬版本
-- (新的 PID), 並把共用庫原始那筆從這間旅行社看得到的清單裡隱藏起來——但這個動作發生時,
-- 這間旅行社原本已經綁在「共用庫舊 PID」上的圖片 (image_asset.matched_pid) 並沒有一併
-- 搬到新的複本 PID。程式碼這邊已經補上這一步 (PoiService.overrideSharedPoi() 新增呼叫
-- ImageAssetDAO.reassignMatchedPid()), 但那只對「這次部署之後才發生」的 override 有效;
-- 部署之前就已經 override 過的舊資料, image_asset.matched_pid 還是停留在已經被隱藏的
-- 舊 PID 上, 需要這支 migration 補跑一次才會修正。
--
-- (「岡山後樂園」這個案例就是這樣: 圖片資源庫頁面用另一個不受可見性限制的查詢方式
-- (PoiService.findById()) 所以還能正確顯示景點名稱, 但景點編輯頁是直接拿「目前正在編輯的
-- PID」(也就是複本 PID) 去查自己的綁定圖片, 查的是圖片實際指向的舊 PID, 完全查不到,
-- 顯示就變成「尚未綁定」。)
--
-- 只搬「這間旅行社自己的」圖片 (JOIN 條件同時比對 AID), 不會影響到別間旅行社剛好也對同一筆
-- 共用庫景點上傳過照片的資料。override_pid 是 NULL 的紀錄代表這間旅行社選擇「隱藏」這筆景點
-- (沒有專屬複本可以搬過去), 這裡用 WHERE po.override_pid IS NOT NULL 排除, 這種情況維持
-- image_asset.matched_pid 不變 (之後若使用者重新編輯這筆景點產生複本, 上面的程式碼修正
-- 會在那個當下補上)。
--
-- 這支是純資料修正 (UPDATE), 沒有改任何欄位結構, 而且是冪等的 (idempotent): 執行完一次後,
-- image_asset.matched_pid 就不會再等於任何 poi_override.original_pid, 之後重複執行這支
-- 檔案 (或不小心重跑) 都只會影響 0 筆, 不會有副作用。
-- ============================================================

UPDATE image_asset ia
JOIN poi_override po ON po.AID = ia.AID AND po.original_pid = ia.matched_pid
SET ia.matched_pid = po.override_pid
WHERE po.override_pid IS NOT NULL;
