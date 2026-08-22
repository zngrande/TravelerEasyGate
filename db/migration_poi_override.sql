-- ============================================================
-- POI 共用庫 + 公司改寫庫分離
-- 放在 useful_travel_schema.sql 之後執行即可。
--
-- 背景: poi.AID 是 NULL 代表平台共用庫 (所有旅行社都能看到/使用), 有值代表該旅行社自建的景點。
-- 但原本編輯/刪除景點完全沒有檢查 AID, 任何旅行社都可以直接改到/刪掉共用庫本身 (影響所有其他旅行社),
-- 甚至改到/刪掉別間旅行社自己建立的私有景點。
--
-- 新做法 (copy-on-write):
-- - 大家都可以繼續使用共用庫 (AID IS NULL) 的景點。
-- - 當某間旅行社「編輯」一筆共用庫景點時, 不會真的改到共用庫本身, 而是複製一份變成該旅行社
--   自己專屬的景點 (poi.AID = 該旅行社), 並在 poi_override 記一筆 original_pid -> override_pid 的對應。
--   之後這間旅行社在列表/搜尋看到的都會是自己的複本, 共用庫原始那筆會被過濾掉；
--   其他旅行社完全不受影響, 還是看得到、也還是共用庫原始那筆。
-- - 當某間旅行社「刪除」一筆共用庫景點時, 不會真的從共用庫刪掉, 只記一筆 override_pid = NULL
--   的紀錄, 代表這間旅行社選擇隱藏它, 之後看不到, 但共用庫資料本身、其他旅行社完全不受影響。
-- - 私有景點 (poi.AID 不是 NULL 且不等於自己): 直接擋掉編輯/刪除, 不開放任何複製/覆寫機制。
-- ============================================================

CREATE TABLE IF NOT EXISTS poi_override (
    OID INT AUTO_INCREMENT PRIMARY KEY,
    AID INT NOT NULL,
    original_pid INT NOT NULL,       -- 共用庫裡原本那筆景點的 PID
    override_pid INT DEFAULT NULL,   -- 該旅行社自己的複本 PID; NULL = 純隱藏、沒有複本 (對應「刪除」)
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_agency_original (AID, original_pid),
    FOREIGN KEY (AID) REFERENCES agency(AID),
    FOREIGN KEY (original_pid) REFERENCES poi(PID) ON DELETE CASCADE,
    FOREIGN KEY (override_pid) REFERENCES poi(PID) ON DELETE CASCADE
) ENGINE=InnoDB;
