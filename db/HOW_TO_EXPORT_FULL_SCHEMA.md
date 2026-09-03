# 怎麼從你現有的資料庫匯出「一份完整、保證正確」的 SQL

## 為什麼不是直接幫你把 30 支 migration 檔案串成一份？

嘗試合併的過程中，發現 `migration_combined.sql`（某次整理時做的合併版）跟
`migration_basic_price_layer.sql` 對不上：前者建立 `quotation_line` 表時欄位直接叫
`net_cost`，但後者預期有一個叫 `basic_quote` 的舊欄位可以改名成 `gross_cost`。如果照
順序硬串起來，會在全新的資料庫上直接失敗（Unknown column 'basic_quote'）。

這代表這 30 支檔案彼此之間**不保證是一致的歷史記錄**，抓到這一個不代表沒有其他的。
在沒有真實資料庫可以實際測試執行的情況下，硬是手動兜一份有實際風險——與其冒風險，
不如直接從「你現在正在用、保證是對的」那個資料庫匯出，100% 準確。

## 做法 (在你自己的機器/伺服器上執行，我這邊沒有連線權限操作)

只匯出「結構」(沒有資料，適合拿去建一個全新的空環境用)：

```bash
mysqldump -u root -p --no-data --routines --triggers \
  traveler_easy_gate > db/full_schema_export.sql
```

如果要連資料一起匯出 (例如要複製一份完整的測試環境)：

```bash
mysqldump -u root -p --routines --triggers \
  traveler_easy_gate > db/full_export_with_data.sql
```

（如果還沒做上次那個資料庫改名，指令裡的 `traveler_easy_gate` 換成 `useful_travel`。）

匯出後你就會有一份**單一檔案**、內容保證跟你現在真正在跑的資料庫一致，之後要在新環境
匯入，直接：

```bash
mysql -u root -p -e "CREATE DATABASE traveler_easy_gate CHARACTER SET utf8mb4"
mysql -u root -p traveler_easy_gate < db/full_schema_export.sql
```

## 之後呢？

從現在開始，`db/` 資料夾裡這些散落的 `migration_*.sql` 就可以功成身退了——**新的資料庫
變更一律走 Flyway** (`src/main/resources/db/migration/`，見上次的說明)，不會再有「一堆檔案
要照順序手動跑、還互相對不上」這種問題。這份匯出的 schema 檔案可以當作乾淨的起點，
之後只要跟著 Flyway 的版本一路疊上去就好。
