package com.example.UsefulTravel.DAO;

import com.example.UsefulTravel.entity.AiImport;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class AiImportDAOImpl implements AiImportDAO {

    private final EntityManager em;

    @Autowired
    public AiImportDAOImpl(EntityManager em) {
        this.em = em;
    }

    @Override
    @Transactional
    public void save(AiImport aiImport) {
        if (aiImport.getCreatedAt() == null) {
            aiImport.setCreatedAt(LocalDateTime.now());
        }
        if (aiImport.getIPID() == 0) {
            em.persist(aiImport);
        } else {
            em.merge(aiImport);
        }
    }

    @Override
    public AiImport findById(int IPID) {
        return em.find(AiImport.class, IPID);
    }

    @Override
    public List<AiImport> findByAgency(int AID) {
        return em.createQuery(
                "SELECT a FROM AiImport a WHERE a.AID = :aid ORDER BY a.createdAt DESC", AiImport.class)
                .setParameter("aid", AID)
                .getResultList();
    }
}
