package com.example.UsefulTravel.DAO;

import com.example.UsefulTravel.entity.Poi;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class PoiDAOImpl implements PoiDAO {

    private final EntityManager em;

    @Autowired
    public PoiDAOImpl(EntityManager em) {
        this.em = em;
    }

    @Override
    @Transactional
    public void save(Poi poi) {
        if (poi.getCreatedAt() == null) {
            poi.setCreatedAt(LocalDateTime.now());
        }
        if (poi.getPID() == 0) {
            em.persist(poi);
        } else {
            em.merge(poi);
        }
    }

    @Override
    public Poi findById(int PID) {
        return em.find(Poi.class, PID);
    }

    // 平台共用庫 (AID IS NULL) + 該旅行社自建的 POI
    @Override
    public List<Poi> findByAgencyOrShared(Integer AID) {
        return em.createQuery(
                "SELECT p FROM Poi p WHERE p.AID IS NULL OR p.AID = :aid ORDER BY p.PID DESC", Poi.class)
                .setParameter("aid", AID)
                .getResultList();
    }

    // 自建行程時, 左側資料庫依「這個行程的國家 + 地區」自動篩選相關景點
    // 國家沒填就退回顯示全部; 地區沒填就只比對國家 (city 用模糊比對, 避免「花蓮」跟「花蓮縣」對不上)
    @Override
    public List<Poi> findByAgencyAndCountry(Integer AID, String country, String region) {
        if (country == null || country.isBlank()) {
            return findByAgencyOrShared(AID);
        }
        StringBuilder jpql = new StringBuilder(
                "SELECT p FROM Poi p WHERE (p.AID IS NULL OR p.AID = :aid) AND p.country = :country ");
        boolean hasRegion = region != null && !region.isBlank();
        if (hasRegion) {
            jpql.append("AND (p.city IS NULL OR p.city LIKE :region) ");
        }
        jpql.append("ORDER BY p.PID DESC");

        var query = em.createQuery(jpql.toString(), Poi.class)
                .setParameter("aid", AID)
                .setParameter("country", country);
        if (hasRegion) {
            query.setParameter("region", "%" + region + "%");
        }
        return query.getResultList();
    }

    @Override
    public List<Poi> searchByKeyword(Integer AID, String keyword, String category) {
        StringBuilder jpql = new StringBuilder(
                "SELECT p FROM Poi p WHERE (p.AID IS NULL OR p.AID = :aid) ");
        if (keyword != null && !keyword.isBlank()) {
            jpql.append("AND (p.name LIKE :kw OR p.city LIKE :kw) ");
        }
        if (category != null && !category.isBlank()) {
            jpql.append("AND p.category = :category ");
        }
        jpql.append("ORDER BY p.PID DESC");

        TypedQuery<Poi> query = em.createQuery(jpql.toString(), Poi.class);
        query.setParameter("aid", AID);
        if (keyword != null && !keyword.isBlank()) {
            query.setParameter("kw", "%" + keyword + "%");
        }
        if (category != null && !category.isBlank()) {
            query.setParameter("category", category);
        }
        return query.getResultList();
    }

    @Override
    @Transactional
    public void deleteById(int PID) {
        Poi poi = em.find(Poi.class, PID);
        if (poi != null) {
            em.remove(poi);
        }
    }
}
