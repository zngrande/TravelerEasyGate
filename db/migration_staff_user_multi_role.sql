-- 員工角色改成可以多選 (例如同時是 EDITOR 又是 QUOTER, 不用整個升成 ADMIN 才能兩件事都做)。
-- staff_user.role 原本是 VARCHAR(20) 存單一角色代碼 (ADMIN/EDITOR/QUOTER/VIEWER),
-- 現在改成存「逗號分隔」的多個角色代碼 (例如 "EDITOR,QUOTER"), 最長的組合
-- "ADMIN,EDITOR,QUOTER,VIEWER" 是 26 個字元, 原本的 VARCHAR(20) 放不下, 需要加寬。
-- 舊資料 (單一角色代碼) 完全相容, 不用另外轉換——PermissionService 的判斷邏輯改成
-- 「逗號切開後裡面有沒有包含 X」, 單一角色代碼本來就是「切開後只有一個元素」的特例。
ALTER TABLE staff_user
  MODIFY COLUMN role VARCHAR(60) DEFAULT 'VIEWER';
