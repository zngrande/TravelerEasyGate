package com.example.UsefulTravel.DAO;

import com.example.UsefulTravel.entity.PriceTierTemplateRow;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class PriceTierTemplateRowDAOImpl implements PriceTierTemplateRowDAO {

    private final EntityManager em;

    @Autowired
    public PriceTierTemplateRowDAOImpl(EntityManager em) {
        this.em = em;
    }

    @Override
    @Transactional
    public void save(PriceTierTemplateRow row) {
        if (row.getPTTRID() == 0) {
            em.persist(row);
        } else {
            em.merge(row);
        }
    }

    @Override
    public List<PriceTierTemplateRow> findByTemplate(int PTTID) {
        return em.createQuery(
                "SELECT r FROM PriceTierTemplateRow r WHERE r.PTTID = :pttid ORDER BY r.sortOrder ASC, r.minQty ASC", PriceTierTemplateRow.class)
                .setParameter("pttid", PTTID)
                .getResultList();
    }

    @Override
    @Transactional
    public void deleteByTemplate(int PTTID) {
        em.createQuery("DELETE FROM PriceTierTemplateRow r WHERE r.PTTID = :pttid")
                .setParameter("pttid", PTTID)
                .executeUpdate();
    }
}
