package com.example.travelereasygate.DAO;

import com.example.travelereasygate.entity.GoogleDistanceCache;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class GoogleDistanceCacheDAOImpl implements GoogleDistanceCacheDAO {

    private static final Logger LOGGER = LoggerFactory.getLogger(GoogleDistanceCacheDAOImpl.class);

    private final EntityManager em;

    @Autowired
    public GoogleDistanceCacheDAOImpl(EntityManager em) {
        this.em = em;
    }

    @Override
    public GoogleDistanceCache find(BigDecimal fromLat, BigDecimal fromLng, BigDecimal toLat, BigDecimal toLng, String mode) {
        try {
            List<GoogleDistanceCache> results = em.createQuery(
                            "SELECT c FROM GoogleDistanceCache c WHERE c.fromLat = :fromLat AND c.fromLng = :fromLng "
                                    + "AND c.toLat = :toLat AND c.toLng = :toLng AND c.mode = :mode",
                            GoogleDistanceCache.class)
                    .setParameter("fromLat", fromLat)
                    .setParameter("fromLng", fromLng)
                    .setParameter("toLat", toLat)
                    .setParameter("toLng", toLng)
                    .setParameter("mode", mode)
                    .setMaxResults(1)
                    .getResultList();
            return results.isEmpty() ? null : results.get(0);
        } catch (Exception e) {
            // 查詢快取失敗 (最典型: migration_google_maps_cache.sql 這張表還沒建立, 或資料庫暫時連不上)
            // 一定要當成「沒命中快取」處理、繼續讓呼叫端正常打 Google API, 不能讓整個距離/地圖功能
            // 因為快取層的問題直接 500——快取原本就只是「錦上添花」的效能優化, 不該變成單點故障。
            LOGGER.warn("查詢 Google 距離快取失敗, 視為未命中 (不影響主流程): {}", e.getMessage());
            return null;
        }
    }

    @Override
    @Transactional
    public void save(GoogleDistanceCache cache) {
        try {
            if (cache.getCachedAt() == null) {
                cache.setCachedAt(LocalDateTime.now());
            }
            em.persist(cache);
        } catch (Exception e) {
            // 寫入快取失敗 (最常見是併發競態撞到 unique key) 不該影響主流程: 這次沒存成功,
            // 下次同樣的座標配對還是會重新呼叫 Google 補上, 不會造成資料錯誤或請求失敗。
            LOGGER.warn("寫入 Google 距離快取失敗, 略過 (不影響主流程): {}", e.getMessage());
        }
    }
}
