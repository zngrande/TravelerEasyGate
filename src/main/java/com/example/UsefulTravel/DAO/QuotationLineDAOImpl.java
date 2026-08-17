package com.example.UsefulTravel.DAO;

import com.example.UsefulTravel.entity.QuotationLine;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class QuotationLineDAOImpl implements QuotationLineDAO {

    private final EntityManager em;

    @Autowired
    public QuotationLineDAOImpl(EntityManager em) {
        this.em = em;
    }

    @Override
    @Transactional
    public void save(QuotationLine line) {
        if (line.getQLID() == 0) {
            em.persist(line);
        } else {
            em.merge(line);
        }
    }

    @Override
    public QuotationLine findById(int QLID) {
        return em.find(QuotationLine.class, QLID);
    }

    @Override
    public List<QuotationLine> findByQuotation(int QID) {
        return em.createQuery(
                "SELECT l FROM QuotationLine l WHERE l.QID = :qid ORDER BY l.sortOrder ASC, l.QLID ASC", QuotationLine.class)
                .setParameter("qid", QID)
                .getResultList();
    }

    @Override
    public QuotationLine findByQuotationAndSourceItem(int QID, int IIID) {
        List<QuotationLine> results = em.createQuery(
                "SELECT l FROM QuotationLine l WHERE l.QID = :qid AND l.sourceItemId = :iiid", QuotationLine.class)
                .setParameter("qid", QID)
                .setParameter("iiid", IIID)
                .setMaxResults(1)
                .getResultList();
        return results.isEmpty() ? null : results.get(0);
    }

    @Override
    @Transactional
    public void delete(QuotationLine line) {
        QuotationLine managed = em.contains(line) ? line : em.merge(line);
        em.remove(managed);
    }

    @Override
    @Transactional
    public void deleteByQuotation(int QID) {
        em.createQuery("DELETE FROM QuotationLine l WHERE l.QID = :qid")
                .setParameter("qid", QID)
                .executeUpdate();
    }
}
