package com.example.travelereasygate.DAO;

import com.example.travelereasygate.entity.QuotationLineTier;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class QuotationLineTierDAOImpl implements QuotationLineTierDAO {

    private final EntityManager em;

    @Autowired
    public QuotationLineTierDAOImpl(EntityManager em) {
        this.em = em;
    }

    @Override
    @Transactional
    public void save(QuotationLineTier tier) {
        if (tier.getQLTID() == 0) {
            em.persist(tier);
        } else {
            em.merge(tier);
        }
    }

    @Override
    public QuotationLineTier findById(int QLTID) {
        return em.find(QuotationLineTier.class, QLTID);
    }

    @Override
    public List<QuotationLineTier> findByLine(int QLID) {
        return em.createQuery(
                "SELECT t FROM QuotationLineTier t WHERE t.QLID = :qlid ORDER BY t.sortOrder ASC, t.minQty ASC", QuotationLineTier.class)
                .setParameter("qlid", QLID)
                .getResultList();
    }

    @Override
    @Transactional
    public void delete(QuotationLineTier tier) {
        QuotationLineTier managed = em.contains(tier) ? tier : em.merge(tier);
        em.remove(managed);
    }

    @Override
    @Transactional
    public void deleteByLine(int QLID) {
        em.createQuery("DELETE FROM QuotationLineTier t WHERE t.QLID = :qlid")
                .setParameter("qlid", QLID)
                .executeUpdate();
    }
}
