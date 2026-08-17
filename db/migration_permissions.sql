-- ============================================================
-- 權限矩陣 (四級) + 行程上鎖 (需求文件第一、二章)
-- 放在 migration_quotation.sql 之後執行即可 (先後順序不影響, 但建議照編號順序執行)
-- ============================================================

-- ------------------------------------------------------------
-- 1. staff_user: 帳號啟用狀態 (「註銷使用者」= 停用, 不是刪除, 避免歷史紀錄斷鏈)
-- ------------------------------------------------------------
ALTER TABLE staff_user ADD COLUMN is_active TINYINT(1) NOT NULL DEFAULT 1;

-- ------------------------------------------------------------
-- 2. 角色資料遷移: 原本 OP/PM/ADMIN 三種, 改成對應需求文件 1.2 權限矩陣的四種角色
--    ADMIN  = 全權限（PM/主管）  : 編輯行程、製作/調整報價、共用槽報價、閱覽 皆可
--    EDITOR = 行程編輯者        : 可編輯行程、可閱覽, 不可碰報價
--    QUOTER = 報價專員          : 可製作/調整報價、可共用槽報價、可閱覽, 不可編輯行程內容
--    VIEWER = 唯讀              : 僅可閱覽
--
--    舊資料沒有「報價專員」這個角色, 舊制度下 OP 本來就能編輯行程,
--    所以遷移時 PM 併入 ADMIN (原本就是主管全權限), OP 併入 EDITOR (維持原本能編輯行程的能力)。
--    未來要把某些 EDITOR 帳號重新指派成 QUOTER 或 VIEWER, 用「使用者權限管理」頁面手動調整即可。
-- ------------------------------------------------------------
UPDATE staff_user SET role = 'ADMIN'  WHERE role = 'PM';
UPDATE staff_user SET role = 'EDITOR' WHERE role = 'OP';

-- ------------------------------------------------------------
-- 3. itinerary: 行程上鎖 (供他人編輯時避免互相覆蓋)
-- ------------------------------------------------------------
ALTER TABLE itinerary ADD COLUMN is_locked TINYINT(1) NOT NULL DEFAULT 0;
ALTER TABLE itinerary ADD COLUMN locked_by INT DEFAULT NULL;   -- staff_user.UID, 誰上的鎖
ALTER TABLE itinerary ADD COLUMN locked_at DATETIME DEFAULT NULL;
ALTER TABLE itinerary ADD CONSTRAINT fk_itinerary_locked_by FOREIGN KEY (locked_by) REFERENCES staff_user(UID);
