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
