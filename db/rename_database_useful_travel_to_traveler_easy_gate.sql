-- ============================================================
-- 資料庫改名: useful_travel → traveler_easy_gate
-- ============================================================
--
-- ⚠️ 重要 - 執行順序:
--   1. 先備份！先備份！先備份！(很重要所以講三次)
--   2. 在還沒改用這次更新的 application.properties 之前, 先手動執行這支腳本
--      (這次更新的 application.properties 已經把連線字串改成連 traveler_easy_gate 這個新名字,
--       如果程式改用新版設定檔、但資料庫還沒改名, 一啟動就會是「Unknown database 'traveler_easy_gate'」
--       整個連不上, 網站會直接掛掉)
--   3. 確認新資料庫運作正常之後, 舊的 useful_travel 資料庫建議先保留個幾天當備份, 不要馬上刪除
--
-- 做法說明: MySQL 沒有直接「改資料庫名稱」的指令, 這裡用的是業界常見、不搬動實際資料只改中繼資料的做法
-- (RENAME TABLE 可以跨資料庫搬移資料表, 幾乎是瞬間完成, 不用整個 dump/restore 一次, 對正式環境比較安全)。
--
-- ------------------------------------------------------------
-- 步驟 1: 建立新的空資料庫
-- ------------------------------------------------------------
CREATE DATABASE IF NOT EXISTS traveler_easy_gate CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- 步驟 2: 把 useful_travel 底下所有資料表都「搬」進 traveler_easy_gate
-- (RENAME TABLE 是中繼資料操作, 不會真的複製資料, 大型資料表也是瞬間完成)
--
-- 下面這段指令幫你把 useful_travel 目前實際有的資料表清單, 自動組成一串 RENAME TABLE 指令——
-- 用 MySQL 內建的 information_schema 查表, 不用自己手動一張一張數，避免漏掉。
-- 執行方式: 用 MySQL 用戶端連進 useful_travel 這個資料庫, 貼上下面這段查詢, 把查出來的結果
-- (應該會是一長串 RENAME TABLE ... TO ... 的完整 SQL 文字) 複製出來, 再貼回去執行一次。
-- ------------------------------------------------------------
SELECT CONCAT(
  'RENAME TABLE ',
  GROUP_CONCAT(
    CONCAT('useful_travel.`', table_name, '` TO traveler_easy_gate.`', table_name, '`')
    SEPARATOR ', '
  ),
  ';'
) AS rename_statement
FROM information_schema.tables
WHERE table_schema = 'useful_travel';

-- 上面查詢出來的結果會長得像這樣 (實際表名依你資料庫現有的為準), 把它複製出來執行:
--   RENAME TABLE useful_travel.`agency` TO traveler_easy_gate.`agency`,
--                useful_travel.`itinerary` TO traveler_easy_gate.`itinerary`,
--                useful_travel.`itinerary_item` TO traveler_easy_gate.`itinerary_item`,
--                ... (其餘資料表以此類推) ...;

-- ------------------------------------------------------------
-- 步驟 3: 確認搬移結果 —— 兩邊資料表數量、資料筆數應該要對得起來
-- ------------------------------------------------------------
-- useful_travel 現在應該是空的 (所有資料表都搬走了):
SELECT COUNT(*) AS remaining_tables_in_old_db
FROM information_schema.tables WHERE table_schema = 'useful_travel';

-- traveler_easy_gate 應該要有完整的資料表:
SELECT COUNT(*) AS tables_in_new_db
FROM information_schema.tables WHERE table_schema = 'traveler_easy_gate';

-- ------------------------------------------------------------
-- 步驟 4: 部署這次更新的程式 (application.properties 已經改成連 traveler_easy_gate)、
--         啟動、實際操作測試幾個常用功能 (登入、看行程列表、開一張報價單) 確認正常。
-- ------------------------------------------------------------

-- ------------------------------------------------------------
-- 步驟 5 (確認一切正常、觀察個幾天沒問題後才做): 刪除已經空了的舊資料庫
-- ------------------------------------------------------------
-- DROP DATABASE useful_travel;
-- (這行故意註解掉, emphasise 要手動確認沒問題才解除註解執行, 不要複製整份腳本一次全部跑完)
