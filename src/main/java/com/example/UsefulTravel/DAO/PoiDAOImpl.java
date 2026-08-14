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
    // country 可能是像「日本、泰國」「印度/不丹」這種多國合併字串 (多國行程, 呼叫端把行程國家
    // 跟每個項目自己的國家全部彙整後傳進來) —— 拆成多個 token 後用 IN 比對任一個相符即可,
    // 不能整包拿去做 exact match (那樣幾乎不可能跟 POI 資料庫裡任何單一國家的值對上, 等於篩不到任何資料)
    @Override
    public List<Poi> findByAgencyAndCountry(Integer AID, String country, String region) {
        List<String> countries = splitCountries(country);
        if (countries.isEmpty()) {
            return findByAgencyOrShared(AID);
        }
        StringBuilder jpql = new StringBuilder(
                "SELECT p FROM Poi p WHERE (p.AID IS NULL OR p.AID = :aid) AND p.country IN :countries ");
        // 多國行程時, 「地區」通常只對應其中一國, 拿去跟全部國家的 POI 一起比對容易誤篩掉其他國家的資料, 故只在單一國家時套用
        boolean hasRegion = countries.size() == 1 && region != null && !region.isBlank();
        if (hasRegion) {
            jpql.append("AND (p.city IS NULL OR p.city LIKE :region) ");
        }
        jpql.append("ORDER BY p.PID DESC");

        var query = em.createQuery(jpql.toString(), Poi.class)
                .setParameter("aid", AID)
                .setParameter("countries", countries);
        if (hasRegion) {
            query.setParameter("region", "%" + region + "%");
        }
        return query.getResultList();
    }

    // 把「日本、泰國」「印度/不丹」這種合併字串拆成乾淨的 token 清單 (去除空白/重複/空字串)
    private List<String> splitCountries(String country) {
        if (country == null || country.isBlank()) return List.of();
        return java.util.Arrays.stream(country.split("[、,，/|]"))
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
