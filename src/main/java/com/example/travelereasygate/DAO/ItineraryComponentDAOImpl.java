package com.example.travelereasygate.DAO;

import com.example.travelereasygate.entity.ItineraryComponent;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ItineraryComponentDAOImpl implements ItineraryComponentDAO {

    private final EntityManager em;

    @Autowired
    public ItineraryComponentDAOImpl(EntityManager em) {
        this.em = em;
    }

    @Override
    @Transactional
    public void save(ItineraryComponent ic) {
        if (ic.getICID() == 0) {
            em.persist(ic);
        } else {
            em.merge(ic);
        }
    }

    @Override
    public List<ItineraryComponent> findByItinerary(int ITID) {
        return em.createQuery(
                "SELECT c FROM ItineraryComponent c WHERE c.ITID = :itid", ItineraryComponent.class)
                .setParameter("itid", ITID)
                .getResultList();
    }
}
