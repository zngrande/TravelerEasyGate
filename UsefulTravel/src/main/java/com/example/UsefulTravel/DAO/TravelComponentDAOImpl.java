package com.example.UsefulTravel.DAO;

import com.example.UsefulTravel.entity.TravelComponent;
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
}
