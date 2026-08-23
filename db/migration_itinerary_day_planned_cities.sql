-- Patch 27: 「建立新行程」頁面的「行程說明」自由文字欄位, 改成逐天指定城市的下拉選單
-- (只列出已選的目的地國家/地區底下的城市), 「AI 安排行程」排某一天時只會從這一天指定的城市底下的
-- 景點/餐廳/飯店挑選, 修正「行程說明打了複雜的多城市文字反而讓 AI 排程失敗」以及「跨天班機那幾天
-- 也被排進一整天觀光行程、餐廳/飯店在不同城市之間混著排」這兩個問題 (詳細原因見
-- claude/patch17-itinerary-new-fixes.md Patch 27 說明)。
--
-- itinerary.description 欄位本身不刪除 (其他既有資料/流程可能還有用到), 這裡只新增
-- itinerary_day.planned_cities: 這一天要安排的城市, 可能不只一個, 用「、」分隔 (跟 country/region
-- 同一套格式), NULL/空字串代表這天沒有指定城市 (通常是班機/轉機日, AI 安排行程時會完全跳過這天)。
ALTER TABLE itinerary_day ADD COLUMN planned_cities VARCHAR(255) NULL AFTER theme;
