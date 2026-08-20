package com.example.UsefulTravel.DAO;

import com.example.UsefulTravel.entity.FormulaTemplateLine;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class FormulaTemplateLineDAOImpl implements FormulaTemplateLineDAO {

    private final EntityManager em;

    @Autowired
    public FormulaTemplateLineDAOImpl(EntityManager em) {
        this.em = em;
    }

    @Override
    @Transactional
    public void save(FormulaTemplateLine line) {
        if (line.getFTLID() == 0) {
            em.persist(line);
        } else {
            em.merge(line);
        }
    }

    @Override
    public List<FormulaTemplateLine> findByTemplate(int FTID) {
        return em.createQuery(
                "SELECT l FROM FormulaTemplateLine l WHERE l.FTID = :ftid", FormulaTemplateLine.class)
                .setParameter("ftid", FTID)
                .getResultList();
    }

    @Override
    @Transactional
    public void deleteByTemplate(int FTID) {
        em.createQuery("DELETE FROM FormulaTemplateLine l WHERE l.FTID = :ftid")
                .setParameter("ftid", FTID)
                .executeUpdate();
    }
}
