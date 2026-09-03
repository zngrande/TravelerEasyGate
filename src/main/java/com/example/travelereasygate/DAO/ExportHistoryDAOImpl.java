package com.example.travelereasygate.DAO;

import com.example.travelereasygate.entity.ExportHistory;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class ExportHistoryDAOImpl implements ExportHistoryDAO {

    private final EntityManager em;

    @Autowired
    public ExportHistoryDAOImpl(EntityManager em) {
        this.em = em;
    }

    @Override
    @Transactional
    public void save(ExportHistory history) {
        if (history.getGeneratedAt() == null) {
            history.setGeneratedAt(LocalDateTime.now());
        }
        em.persist(history);
    }

    @Override
    public List<ExportHistory> findByItinerary(int ITID) {
        return em.createQuery(
                "SELECT h FROM ExportHistory h WHERE h.ITID = :itid ORDER BY h.generatedAt DESC", ExportHistory.class)
                .setParameter("itid", ITID)
                .getResultList();
    }
}
