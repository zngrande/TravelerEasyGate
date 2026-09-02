-- 新增: 交通類項目 (item_type = 'transport') 可以額外記錄「航班/車次編號」，
-- 例如「CI100」「新幹線のぞみ23号」，跟既有的 transport_method (飛機/高鐵/遊覽車...) 分開存，
-- 不影響舊資料 (NULL 代表沒填, 顯示邏輯會自動退回原本「A機場 → B機場」的格式，不會顯示空白編號)。
ALTER TABLE itinerary_item
  ADD COLUMN transport_number VARCHAR(50) NULL AFTER transport_method;
