-- ============================================================
-- 完整定價鏈路確定為 5 層 (需求文件最新版本):
--
--   Net 總成本   = 原始牌價金額 (單價 × 數量, 完全沒調整過)
--   NNet 總淨成本 = 扣掉 FOC 折抵/折讓/返利後, 真正掏出來的最終進貨成本
--   基本報價     = NNet + 基本利潤 (新增的一層, 疊在 NNet 上面)
--   總同業價     = 基本報價 + 同業預留利潤 (疊在基本報價上面, 不是直接疊在 NNet 上面)
--   總直售價     = 總同業價 + 直售附加利潤 (疊在同業價上面, 沿用之前就有的疊加設計)
--
-- 這次異動:
--   1. quotation_line.basic_quote 改名成 gross_cost (它一直都是「原始牌價/Net 總成本」這個概念,
--      只是之前借用了「基本報價」這個名字, 現在「基本報價」被重新定義成新的一層, 名字要讓開)
--   2. quotation_line 新增 basic_price, 對應新的「基本報價」這一層 (NNet+基本利潤, 依成本佔比分攤)
--   3. quotation 新增 basic_markup_mode / basic_markup_value, 邏輯跟 trade/retail 那兩層一樣
--      (PERCENT: 乘 NNet 的百分比; AMOUNT: 直接自填一個固定金額)
-- ============================================================
ALTER TABLE quotation_line CHANGE COLUMN basic_quote gross_cost DECIMAL(14,2) NOT NULL DEFAULT 0;
ALTER TABLE quotation_line ADD COLUMN basic_price DECIMAL(14,2) NOT NULL DEFAULT 0;

ALTER TABLE quotation ADD COLUMN basic_markup_mode VARCHAR(10) NOT NULL DEFAULT 'PERCENT';  -- PERCENT / AMOUNT
ALTER TABLE quotation ADD COLUMN basic_markup_value DECIMAL(14,2) NOT NULL DEFAULT 0;
