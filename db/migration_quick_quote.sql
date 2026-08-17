-- ============================================================
-- 簡易報價編輯頁: 讓報價項目可以連結到行程裡的某個景點/餐廳/飯店項目
-- 放在 migration_price_tier.sql 之後執行即可
--
-- 設計說明: 簡易報價頁跟正式報價頁 (/quotation/{qid}) 讀寫的是同一份 quotation_line 資料,
-- 不是另外開一套。差別只在於簡易頁面多了 source_item_id 這個連結——
-- 填價錢時如果這個項目已經連過一筆報價明細, 就更新原本那筆, 不會重複新增。
-- source_item_id 是 NULL 的那些明細 (雜費/機票/自訂項目) 則兩邊頁面共用同一套「無來源」邏輯,
-- 不需要額外欄位。
-- ============================================================

ALTER TABLE quotation_line ADD COLUMN source_item_id INT DEFAULT NULL;
ALTER TABLE quotation_line ADD CONSTRAINT fk_qline_source_item
    FOREIGN KEY (source_item_id) REFERENCES itinerary_item(IIID) ON DELETE SET NULL;
-- 行程項目被刪除時只解除連結、不連帶刪除報價明細, 避免已經填好的成本資料無聲消失

CREATE INDEX idx_qline_source_item ON quotation_line(source_item_id);
