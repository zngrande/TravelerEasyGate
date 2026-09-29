package com.example.travelereasygate.DAO;

import com.example.travelereasygate.entity.GooglePolylineCache;
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
public class GooglePolylineCacheDAOImpl implements GooglePolylineCacheDAO {

    private static final Logger LOGGER = LoggerFactory.getLogger(GooglePolylineCacheDAOImpl.class);

    private final EntityManager em;

    @Autowired
    public GooglePolylineCacheDAOImpl(EntityManager em) {
        this.em = em;
    }

    @Override
    public GooglePolylineCache find(BigDecimal fromLat, BigDecimal fromLng, BigDecimal toLat, BigDecimal toLng, String mode) {
        try {
            List<GooglePolylineCache> results = em.createQuery(
                            "SELECT c FROM GooglePolylineCache c WHERE c.fromLat = :fromLat AND c.fromLng = :fromLng "
                                    + "AND c.toLat = :toLat AND c.toLng = :toLng AND c.mode = :mode",
                            GooglePolylineCache.class)
                    .setParameter("fromLat", fromLat)
                    .setParameter("fromLng", fromLng)
                    .setParameter("toLat", toLat)
                    .setParameter("toLng", toLng)
                    .setParameter("mode", mode)
                    .setMaxResults(1)
                    .getResultList();
            return results.isEmpty() ? null : results.get(0);
        } catch (Exception e) {
            // 理由同 GoogleDistanceCacheDAOImpl.find(): 快取查詢失敗要當成沒命中, 不能讓整個看板地圖
            // 功能因為快取表還沒建立 (migration 還沒跑) 或資料庫暫時性問題就直接 500。
            LOGGER.warn("查詢 Google 折線快取失敗, 視為未命中 (不影響主流程): {}", e.getMessage());
            return null;
        }
    }

    @Override
    @Transactional
    public void save(GooglePolylineCache cache) {
        try {
            if (cache.getCachedAt() == null) {
                cache.setCachedAt(LocalDateTime.now());
            }
            em.persist(cache);
        } catch (Exception e) {
            LOGGER.warn("寫入 Google 折線快取失敗, 略過 (不影響主流程): {}", e.getMessage());
        }
    }
}
