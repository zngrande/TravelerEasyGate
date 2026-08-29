package com.example.UsefulTravel.service;

import com.example.UsefulTravel.DAO.CurrencyDAO;
import com.example.UsefulTravel.entity.Currency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * ExchangeRateUpdateService - 每天自動更新匯率 (報價單用)
 *
 * 背景: Currency.rateToTwd 原本完全靠手動填寫, 使用者反映希望能每天自動更新, 不用自己去查匯率、
 * 手動改資料庫。改成: 每天固定時間打一次免費匯率 API (ExchangeRateClient), 更新「平台共用」的
 * 匯率資料 (AID = NULL 那幾筆)。
 *
 * 範圍只更新平台共用匯率, 不動旅行社自訂匯率的原因: 旅行社會自訂匯率通常是刻意要跟市場匯率有落差
 * (例如報價要留一點匯差當緩衝、或內部長期用固定匯率方便對帳), 這是使用者主動的商業決定, 自動排程
 * 不應該覆蓋掉; 如果之後有旅行社反映也想要自己的自訂匯率跟著自動更新, 再另外開放選項即可。
 *
 * 只更新「平台共用匯率表裡已經存在的貨幣」, 不會自動新增資料庫沒有的幣別代碼 (避免造出使用者
 * 完全沒在用、報價單也選不到的雜訊資料; 有新幣別需求時到匯率設定頁手動新增一筆, 之後就會自動更新)。
 */
@Service
public class ExchangeRateUpdateService {

    private static final Logger log = LoggerFactory.getLogger(ExchangeRateUpdateService.class);

    private final ExchangeRateClient exchangeRateClient;
    private final CurrencyDAO currencyDAO;

    @Autowired
    public ExchangeRateUpdateService(ExchangeRateClient exchangeRateClient, CurrencyDAO currencyDAO) {
        this.exchangeRateClient = exchangeRateClient;
        this.currencyDAO = currencyDAO;
    }

    // 每天早上 07:10 (台北時間, 伺服器時區設定為 Asia/Taipei 才會準; 避開整點跟其他排程/備份撞在一起)
    // 執行一次。來源 API 本身大約每天更新一次, 抓太多次沒有意義, 選在營業時間前更新確保線控一早上班
    // 開始報價時看到的就是當天最新匯率。
    @Scheduled(cron = "0 10 7 * * *", zone = "Asia/Taipei")
    public void updateSharedCurrencyRates() {
        Map<String, BigDecimal> latestRates = exchangeRateClient.fetchRatesToTwd();
        if (latestRates == null) {
            log.warn("[匯率自動更新] 呼叫匯率 API 失敗或回傳異常, 本次跳過, 沿用現有匯率, 等明天排程再試一次");
            return;
        }

        List<Currency> sharedCurrencies = currencyDAO.findAll().stream()
                .filter(c -> c.getAID() == null)
                .toList();

        int updated = 0;
        for (Currency currency : sharedCurrencies) {
            if ("TWD".equalsIgnoreCase(currency.getCode())) continue; // TWD 對 TWD 固定是 1, 不需要更新
            BigDecimal newRate = latestRates.get(currency.getCode());
            if (newRate == null) continue; // API 沒有這個幣別代碼的資料, 保留原本的值不動

            currency.setRateToTwd(newRate);
            currency.setUpdatedAt(LocalDateTime.now());
            currencyDAO.save(currency);
            updated++;
        }
        log.info("[匯率自動更新] 完成, 共更新 {} 筆平台共用匯率", updated);
    }

    /**
     * 給管理後台「立即更新」按鈕用 (如果之後要加的話), 或手動觸發測試, 回傳實際更新的幣別數量。
     */
    public int triggerManualUpdate() {
        updateSharedCurrencyRates();
        return (int) currencyDAO.findAll().stream()
                .filter(c -> c.getAID() == null && c.getUpdatedAt() != null
                        && c.getUpdatedAt().toLocalDate().isEqual(LocalDateTime.now().toLocalDate()))
                .count();
    }
}
