package com.example.travelereasygate.DAO;

import com.example.travelereasygate.entity.Quotation;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class QuotationDAOImpl implements QuotationDAO {

    private final EntityManager em;

    @Autowired
    public QuotationDAOImpl(EntityManager em) {
        this.em = em;
    }

    @Override
    @Transactional
    public void save(Quotation quotation) {
        if (quotation.getQID() == 0) {
            em.persist(quotation);
        } else {
            em.merge(quotation);
        }
    }

    @Override
    public Quotation findById(int QID) {
        return em.find(Quotation.class, QID);
    }

    @Override
    public List<Quotation> findByItinerary(int ITID) {
        return em.createQuery(
                "SELECT q FROM Quotation q WHERE q.ITID = :itid ORDER BY q.version DESC", Quotation.class)
                .setParameter("itid", ITID)
                .getResultList();
    }

    @Override
    public int nextVersion(int ITID) {
        Integer maxVersion = em.createQuery(
                "SELECT MAX(q.version) FROM Quotation q WHERE q.ITID = :itid", Integer.class)
                .setParameter("itid", ITID)
                .getSingleResult();
        return (maxVersion == null ? 0 : maxVersion) + 1;
    }

    @Override
    @Transactional
    public void delete(Quotation quotation) {
        Quotation managed = em.contains(quotation) ? quotation : em.merge(quotation);
        em.remove(managed);
    }
}
