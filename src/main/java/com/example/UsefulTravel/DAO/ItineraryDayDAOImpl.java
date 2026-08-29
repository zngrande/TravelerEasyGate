package com.example.UsefulTravel.DAO;

import com.example.UsefulTravel.entity.ItineraryDay;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ItineraryDayDAOImpl implements ItineraryDayDAO {

    private final EntityManager em;

    @Autowired
    public ItineraryDayDAOImpl(EntityManager em) {
        this.em = em;
    }

    @Override
    @Transactional
    public void save(ItineraryDay day) {
        if (day.getIDID() == 0) {
            em.persist(day);
        } else {
            em.merge(day);
        }
    }

    @Override
    public ItineraryDay findById(int IDID) {
        return em.find(ItineraryDay.class, IDID);
    }

    @Override
    public List<ItineraryDay> findByItinerary(int ITID) {
        return em.createQuery(
                "SELECT d FROM ItineraryDay d WHERE d.ITID = :itid ORDER BY d.dayNumber ASC", ItineraryDay.class)
                .setParameter("itid", ITID)
                .getResultList();
    }

    @Override
    @Transactional
    public void deleteById(int IDID) {
        ItineraryDay day = em.find(ItineraryDay.class, IDID);
        if (day != null) em.remove(day);
    }
}
