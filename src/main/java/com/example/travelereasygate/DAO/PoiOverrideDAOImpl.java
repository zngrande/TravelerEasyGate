package com.example.travelereasygate.DAO;

import com.example.travelereasygate.entity.PoiOverride;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public class PoiOverrideDAOImpl implements PoiOverrideDAO {

    private final EntityManager em;

    @Autowired
    public PoiOverrideDAOImpl(EntityManager em) {
        this.em = em;
    }

    @Override
    @Transactional
    public void save(PoiOverride override) {
        if (override.getCreatedAt() == null) {
            override.setCreatedAt(LocalDateTime.now());
        }
        if (override.getOID() == 0) {
            em.persist(override);
        } else {
            em.merge(override);
        }
    }

    @Override
    public PoiOverride findByAgencyAndOriginal(int AID, int originalPid) {
        try {
            return em.createQuery(
                    "SELECT po FROM PoiOverride po WHERE po.AID = :aid AND po.originalPid = :originalPid",
                    PoiOverride.class)
                    .setParameter("aid", AID)
                    .setParameter("originalPid", originalPid)
                    .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }
}
