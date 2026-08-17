package com.example.UsefulTravel.DAO;

import com.example.UsefulTravel.entity.QuotationGroupTier;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class QuotationGroupTierDAOImpl implements QuotationGroupTierDAO {

    private final EntityManager em;

    @Autowired
    public QuotationGroupTierDAOImpl(EntityManager em) {
        this.em = em;
    }

    @Override
    @Transactional
    public void save(QuotationGroupTier tier) {
        if (tier.getQGTID() == 0) {
            em.persist(tier);
        } else {
            em.merge(tier);
        }
    }

    @Override
    public QuotationGroupTier findById(int QGTID) {
        return em.find(QuotationGroupTier.class, QGTID);
    }

    @Override
    public List<QuotationGroupTier> findByQuotation(int QID) {
        return em.createQuery(
                        "SELECT t FROM QuotationGroupTier t WHERE t.QID = :qid ORDER BY t.sortOrder ASC, t.minQty ASC", QuotationGroupTier.class)
                .setParameter("qid", QID)
                .getResultList();
    }

    @Override
    @Transactional
    public void delete(QuotationGroupTier tier) {
        QuotationGroupTier managed = em.contains(tier) ? tier : em.merge(tier);
        em.remove(managed);
    }

    @Override
    @Transactional
    public void deleteByQuotation(int QID) {
        em.createQuery("DELETE FROM QuotationGroupTier t WHERE t.QID = :qid")
                .setParameter("qid", QID)
                .executeUpdate();
    }
}