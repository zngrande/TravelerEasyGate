package com.example.UsefulTravel.DAO;

import com.example.UsefulTravel.entity.Itinerary;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class ItineraryDAOImpl implements ItineraryDAO {

    private final EntityManager em;

    @Autowired
    public ItineraryDAOImpl(EntityManager em) {
        this.em = em;
    }

    @Override
    @Transactional
    public void save(Itinerary itinerary) {
        LocalDateTime now = LocalDateTime.now();
        if (itinerary.getCreatedAt() == null) {
            itinerary.setCreatedAt(now);
        }
        itinerary.setUpdatedAt(now);

        if (itinerary.getITID() == 0) {
            em.persist(itinerary);
        } else {
            em.merge(itinerary);
        }
    }

    @Override
    public Itinerary findById(int ITID) {
        return em.find(Itinerary.class, ITID);
    }

    @Override
    public List<Itinerary> findByAgency(int AID) {
        return em.createQuery(
                "SELECT i FROM Itinerary i WHERE i.AID = :aid ORDER BY i.updatedAt DESC", Itinerary.class)
                .setParameter("aid", AID)
                .getResultList();
    }

    @Override
    @Transactional
    public void deleteById(int ITID) {
        Itinerary itinerary = em.find(Itinerary.class, ITID);
        if (itinerary != null) {
            em.remove(itinerary);
        }
    }
}
