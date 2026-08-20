package com.example.UsefulTravel.DAO;

import com.example.UsefulTravel.entity.FormulaTemplate;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class FormulaTemplateDAOImpl implements FormulaTemplateDAO {

    private final EntityManager em;

    @Autowired
    public FormulaTemplateDAOImpl(EntityManager em) {
        this.em = em;
    }

    @Override
    @Transactional
    public void save(FormulaTemplate template) {
        if (template.getFTID() == 0) {
            if (template.getCreatedAt() == null) {
                template.setCreatedAt(java.time.LocalDateTime.now());
            }
            em.persist(template);
        } else {
            em.merge(template);
        }
    }

    @Override
    public FormulaTemplate findById(int FTID) {
        return em.find(FormulaTemplate.class, FTID);
    }

    @Override
    public List<FormulaTemplate> findByAgency(int AID) {
        return em.createQuery(
                "SELECT t FROM FormulaTemplate t WHERE t.AID = :aid ORDER BY t.name ASC", FormulaTemplate.class)
                .setParameter("aid", AID)
                .getResultList();
    }

    @Override
    @Transactional
    public void delete(FormulaTemplate template) {
        FormulaTemplate managed = em.contains(template) ? template : em.merge(template);
        em.remove(managed);
    }
}
