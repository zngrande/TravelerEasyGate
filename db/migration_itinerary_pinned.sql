-- Patch 37: 首頁「我的行程」列表新增「釘選」功能 (像 LINE 聊天列表往右滑釘選)，
-- 釘選的行程要排在列表最上面，取消釘選才會退回原本「最近更新排前面」的順序。
--
-- pinned: 這個行程有沒有被釘選 (預設 false，不影響現有資料)
-- pinned_at: 釘選當下的時間點，讓多筆同時釘選的行程之間也能排序 (最近釘選排最前面)；
--            取消釘選時會被清成 NULL，跟 pinned=false 一定同步。
ALTER TABLE itinerary ADD COLUMN pinned TINYINT(1) NOT NULL DEFAULT 0 AFTER description;
ALTER TABLE itinerary ADD COLUMN pinned_at DATETIME NULL AFTER pinned;
