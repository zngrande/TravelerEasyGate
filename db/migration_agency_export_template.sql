-- 新增：每個旅行社(帳號)可以上傳自己的行程輸出範本 (.docx)
-- 放在 useful_travel_schema.sql 的 export_history 表之後即可

CREATE TABLE agency_export_template (
    AETID INT AUTO_INCREMENT PRIMARY KEY,
    AID INT NOT NULL,                          -- 屬於哪個旅行社帳號
    name VARCHAR(100) NOT NULL,                 -- 範本名稱, 方便同一家旅行社管理多份範本 (e.g. 熱門版/精緻版)
    file_path VARCHAR(255) NOT NULL,            -- 範本 .docx 實體存放路徑 (沿用 ImageStorageService 的儲存方式)
    is_default TINYINT(1) NOT NULL DEFAULT 0,   -- 這家旅行社匯出時預設用哪一份範本
    uploaded_by INT,                            -- staff_user.UID
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_aet_agency FOREIGN KEY (AID) REFERENCES agency(AID),
    CONSTRAINT fk_aet_uploader FOREIGN KEY (uploaded_by) REFERENCES staff_user(UID)
);

CREATE INDEX idx_aet_agency ON agency_export_template(AID);
