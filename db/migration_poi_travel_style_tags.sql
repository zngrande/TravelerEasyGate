-- 景點、餐廳與飯店可標記旅行風格，供 AI 行程自動編排優先挑選。
ALTER TABLE poi ADD COLUMN travel_style_tags TEXT NULL;
