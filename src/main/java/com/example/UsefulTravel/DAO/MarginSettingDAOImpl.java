package com.example.UsefulTravel.DAO;

import com.example.UsefulTravel.entity.MarginSetting;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class MarginSettingDAOImpl implements MarginSettingDAO {

    private final EntityManager em;

    @Autowired
    public MarginSettingDAOImpl(EntityManager em) {
        this.em = em;
    }

    @Override
    @Transactional
    public void save(MarginSetting setting) {
        if (setting.getMSID() == 0) {
            em.persist(setting);
        } else {
            em.merge(setting);
        }
    }

    @Override
    public MarginSetting findById(int MSID) {
        return em.find(MarginSetting.class, MSID);
    }

    @Override
    public List<MarginSetting> findByAgency(int AID) {
        return em.createQuery(
                        "SELECT m FROM MarginSetting m WHERE m.AID = :aid ORDER BY m.isDefault DESC, m.name ASC", MarginSetting.class)
                .setParameter("aid", AID)
                .getResultList();
    }

    @Override
    public MarginSetting findDefault(int AID) {
        try {
            return em.createQuery(
                            "SELECT m FROM MarginSetting m WHERE m.AID = :aid AND m.isDefault = true", MarginSetting.class)
                    .setParameter("aid", AID)
                    .setMaxResults(1)
                    .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    @Override
    @Transactional
    public void deleteById(int MSID) {
        MarginSetting setting = em.find(MarginSetting.class, MSID);
        if (setting != null) {
            em.remove(setting);
        }
    }

    @Override
    @Transactional
    public void clearDefault(int AID) {
        em.createQuery("UPDATE MarginSetting m SET m.isDefault = false WHERE m.AID = :aid")
                .setParameter("aid", AID)
                .executeUpdate();
    }

    @Override
    public long countQuotationUsage(int MSID) {
        return em.createQuery(
                        "SELECT COUNT(q) FROM Quotation q WHERE q.MSID = :msid", Long.class)
                .setParameter("msid", MSID)
                .getSingleResult();
    }
}