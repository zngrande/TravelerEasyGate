package com.example.travelereasygate.DAO;

import com.example.travelereasygate.entity.PriceTierTemplate;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class PriceTierTemplateDAOImpl implements PriceTierTemplateDAO {

    private final EntityManager em;

    @Autowired
    public PriceTierTemplateDAOImpl(EntityManager em) {
        this.em = em;
    }

    @Override
    @Transactional
    public void save(PriceTierTemplate template) {
        if (template.getPTTID() == 0) {
            if (template.getCreatedAt() == null) {
                template.setCreatedAt(java.time.LocalDateTime.now());
            }
            em.persist(template);
        } else {
            em.merge(template);
        }
    }

    @Override
    public PriceTierTemplate findById(int PTTID) {
        return em.find(PriceTierTemplate.class, PTTID);
    }

    @Override
    public List<PriceTierTemplate> findByAgency(int AID) {
        return em.createQuery(
                "SELECT t FROM PriceTierTemplate t WHERE t.AID = :aid ORDER BY t.name ASC", PriceTierTemplate.class)
                .setParameter("aid", AID)
                .getResultList();
    }

    @Override
    @Transactional
    public void delete(PriceTierTemplate template) {
        PriceTierTemplate managed = em.contains(template) ? template : em.merge(template);
        em.remove(managed);
    }
}
