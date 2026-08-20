-- ============================================================
-- 同業價/直售價改成疊加式計算, 且每張報價單自己可獨立調整 (不再只能套用加成規則範本的固定%數)
--
--   基本報價 (NNet) = 報價項目明細加總的淨成本 (沿用原本就有的 net_cost 加總, 沒有新增欄位)
--   同業價 = 基本報價 + 同業利潤   (trade_markup_mode=PERCENT: 利潤=基本報價×trade_markup_value%;
--                                    trade_markup_mode=AMOUNT : 利潤=trade_markup_value 這個固定金額)
--   直售價 = 同業價 + 直售(Agent)利潤 (retail_markup_mode 同上, 但疊在「同業價」上面, 不是直接從基本報價算,
--                                       這是跟原本「同業價/直售價各自獨立從成本算」最大的不同)
--
--   新增報價單時, 這四個欄位的初始值會從當時選的加成規則帶入 (mode 都預設 PERCENT,
--   value 帶入範本的 trade_markup_pct / retail_markup_pct), 之後這張報價單自己可以獨立再調整,
--   不會因為之後加成規則範本改了就跟著變 (符合「金額凍結快照」精神, 只是快照的起點更早)。
-- ============================================================
ALTER TABLE quotation ADD COLUMN trade_markup_mode VARCHAR(10) NOT NULL DEFAULT 'PERCENT';   -- PERCENT / AMOUNT
ALTER TABLE quotation ADD COLUMN trade_markup_value DECIMAL(14,2) NOT NULL DEFAULT 0;
ALTER TABLE quotation ADD COLUMN retail_markup_mode VARCHAR(10) NOT NULL DEFAULT 'PERCENT';  -- PERCENT / AMOUNT
ALTER TABLE quotation ADD COLUMN retail_markup_value DECIMAL(14,2) NOT NULL DEFAULT 0;

-- 把既有報價單的初始值, 用它原本掛的加成規則範本帶進去 (沒有掛範本的維持 0)
UPDATE quotation q
JOIN margin_setting ms ON ms.MSID = q.MSID
SET q.trade_markup_value = ms.trade_markup_pct,
    q.retail_markup_value = ms.retail_markup_pct
WHERE q.MSID IS NOT NULL;
