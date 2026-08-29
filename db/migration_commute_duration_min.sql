-- 交通項目的「通勤時間」改成數字 (分鐘), 取代原本的自由文字欄位 commute_duration。
-- 用數字才能真的帶入行程時間表 (時間軸) 的計算, 跟停留時間 (stay_duration_min) 是同一種做法：
-- 如果這個項目有填出發時間/抵達時間 (start_time/end_time), 時間表計算時仍然優先採用那兩個真實時刻，
-- 只有兩者都沒填、且沒有走路/開車拉車距離可以估算時，才會退回用這個通勤時間分鐘數估算。
-- 原本的 commute_duration (VARCHAR, 自由文字) 保留不動，只是畫面上已經不會再讀寫它，
-- 不會影響任何舊資料，也不需要額外做資料轉換/搬移。
ALTER TABLE itinerary_item ADD COLUMN IF NOT EXISTS commute_duration_min INT DEFAULT NULL;
