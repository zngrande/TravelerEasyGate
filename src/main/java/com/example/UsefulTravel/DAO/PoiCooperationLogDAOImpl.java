package com.example.UsefulTravel.DAO;

import com.example.UsefulTravel.entity.PoiCooperationLog;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class PoiCooperationLogDAOImpl implements PoiCooperationLogDAO {

    private final EntityManager em;

    @Autowired
    public PoiCooperationLogDAOImpl(EntityManager em) {
        this.em = em;
    }

    @Override
    @Transactional
    public void save(PoiCooperationLog log) {
        if (log.getCreatedAt() == null) {
            log.setCreatedAt(LocalDateTime.now());
        }
        em.persist(log);
    }

    @Override
    public List<PoiCooperationLog> findByPoi(int PID) {
        return em.createQuery(
                "SELECT l FROM PoiCooperationLog l WHERE l.PID = :pid ORDER BY l.createdAt DESC", PoiCooperationLog.class)
                .setParameter("pid", PID)
                .getResultList();
    }
}
