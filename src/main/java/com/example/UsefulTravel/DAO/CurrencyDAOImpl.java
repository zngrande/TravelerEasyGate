package com.example.UsefulTravel.DAO;

import com.example.UsefulTravel.entity.Currency;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class CurrencyDAOImpl implements CurrencyDAO {

    private final EntityManager em;

    @Autowired
    public CurrencyDAOImpl(EntityManager em) {
        this.em = em;
    }

    @Override
    @Transactional
    public void save(Currency currency) {
        if (currency.getCID() == 0) {
            em.persist(currency);
        } else {
            em.merge(currency);
        }
    }

    @Override
    public Currency findById(int CID) {
        return em.find(Currency.class, CID);
    }

    @Override
    public Currency findByCode(String code, Integer AID) {
        // 先找該旅行社自訂匯率
        if (AID != null) {
            try {
                return em.createQuery(
                        "SELECT c FROM Currency c WHERE c.code = :code AND c.AID = :aid", Currency.class)
                        .setParameter("code", code)
                        .setParameter("aid", AID)
                        .getSingleResult();
            } catch (NoResultException ignored) {
                // 沒有自訂匯率, 往下退回平台共用匯率
            }
        }
        try {
            return em.createQuery(
                    "SELECT c FROM Currency c WHERE c.code = :code AND c.AID IS NULL", Currency.class)
                    .setParameter("code", code)
                    .getSingleResult();
        } catch (NoResultException ignored) {
            return null;
        }
    }

    @Override
    public List<Currency> findAvailable(Integer AID) {
        return em.createQuery(
                "SELECT c FROM Currency c WHERE c.AID IS NULL OR c.AID = :aid ORDER BY c.code ASC", Currency.class)
                .setParameter("aid", AID)
                .getResultList();
    }
}
