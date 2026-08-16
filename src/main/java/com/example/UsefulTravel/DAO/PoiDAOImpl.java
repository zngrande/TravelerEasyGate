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
    //
    // country / region 都可能是「日本、泰國」「熊本 福岡」這種用頓號/逗號/斜線/空格混著分隔的多個地名合併字串
    // (多國行程, 或使用者一次打好幾個地區時) —— 拆成多個 token 後用「符合任一個」比對, 不能整包拿去做
    // exact match / LIKE (那樣幾乎不可能跟資料庫裡任何單一地名的值對上, 等於篩不到任何資料)
    @Override
    public List<Poi> findByAgencyAndCountry(Integer AID, String country, String region) {
        List<String> countries = splitLocationTokens(country);
        if (countries.isEmpty()) {
            return findByAgencyOrShared(AID);
        }
        StringBuilder jpql = new StringBuilder(
                "SELECT p FROM Poi p WHERE (p.AID IS NULL OR p.AID = :aid) AND p.country IN :countries ");
        // 多國行程時, 「地區」通常只對應其中一國, 拿去跟全部國家的 POI 一起比對容易誤篩掉其他國家的資料, 故只在單一國家時套用
        List<String> regions = (countries.size() == 1) ? splitLocationTokens(region) : List.of();
        boolean hasRegion = !regions.isEmpty();
        if (hasRegion) {
            StringBuilder regionClause = new StringBuilder("AND (p.city IS NULL");
            for (int i = 0; i < regions.size(); i++) {
                regionClause.append(" OR p.city LIKE :region").append(i);
            }
            regionClause.append(") ");
            jpql.append(regionClause);
        }
        jpql.append("ORDER BY p.PID DESC");

        var query = em.createQuery(jpql.toString(), Poi.class)
                .setParameter("aid", AID)
                .setParameter("countries", countries);
        if (hasRegion) {
            for (int i = 0; i < regions.size(); i++) {
                query.setParameter("region" + i, "%" + regions.get(i) + "%");
            }
        }
        return query.getResultList();
    }

    // 把「日本、泰國」「熊本 福岡」「印度/不丹」這種用頓號、逗號(全形/半形)、斜線、直線或空格混著分隔的
    // 合併字串拆成乾淨的 token 清單 (去除空白/重複/空字串); 國家、地區欄位共用同一套拆解規則
    private List<String> splitLocationTokens(String value) {
        if (value == null || value.isBlank()) return List.of();
        return java.util.Arrays.stream(value.split("[、,，/|\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(java.util.stream.Collectors.toList());
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