-- 合作廠商優先安排標記。
ALTER TABLE poi ADD COLUMN partner_priority BOOLEAN NOT NULL DEFAULT FALSE;
