package com.example.travelereasygate.DAO;

import com.example.travelereasygate.entity.TravelComponent;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class TravelComponentDAOImpl implements TravelComponentDAO {

    private final EntityManager em;

    @Autowired
    public TravelComponentDAOImpl(EntityManager em) {
        this.em = em;
    }

    @Override
    @Transactional
    public void save(TravelComponent component) {
        if (component.getCPID() == 0) {
            em.persist(component);
        } else {
            em.merge(component);
        }
    }

    @Override
    public TravelComponent findById(int CPID) {
        return em.find(TravelComponent.class, CPID);
    }

    @Override
    public List<TravelComponent> findByAgency(int AID) {
        return em.createQuery(
                        "SELECT c FROM TravelComponent c WHERE c.AID = :aid ORDER BY c.type ASC", TravelComponent.class)
                .setParameter("aid", AID)
                .getResultList();
    }

    @Override
    @Transactional
    public void deleteById(int CPID) {
        TravelComponent component = em.find(TravelComponent.class, CPID);
        if (component != null) {
            em.remove(component);
        }
    }

    @Override
    public long countItineraryUsage(int CPID) {
        return em.createQuery(
                        "SELECT COUNT(ic) FROM ItineraryComponent ic WHERE ic.CPID = :cpid", Long.class)
                .setParameter("cpid", CPID)
                .getSingleResult();
    }

    @Override
    @Transactional
    public void clearQuotationLineReferences(int CPID) {
        em.createQuery("UPDATE QuotationLine l SET l.CPID = NULL WHERE l.CPID = :cpid")
                .setParameter("cpid", CPID)
                .executeUpdate();
    }
}