package com.example.travelereasygate.DAO;

import com.example.travelereasygate.entity.ItineraryItemOption;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ItineraryItemOptionDAOImpl implements ItineraryItemOptionDAO {

    private final EntityManager em;

    @Autowired
    public ItineraryItemOptionDAOImpl(EntityManager em) {
        this.em = em;
    }

    @Override
    @Transactional
    public void save(ItineraryItemOption option) {
        if (option.getIIOID() == 0) {
            em.persist(option);
        } else {
            em.merge(option);
        }
    }

    @Override
    public List<ItineraryItemOption> findByItem(int IIID) {
        return em.createQuery(
                "SELECT o FROM ItineraryItemOption o WHERE o.IIID = :iiid ORDER BY o.IIOID ASC", ItineraryItemOption.class)
                .setParameter("iiid", IIID)
                .getResultList();
    }

    @Override
    @Transactional
    public void deleteByItem(int IIID) {
        em.createQuery("DELETE FROM ItineraryItemOption o WHERE o.IIID = :iiid")
                .setParameter("iiid", IIID)
                .executeUpdate();
    }

    @Override
    @Transactional
    public void clearSelected(int IIID) {
        em.createQuery("UPDATE ItineraryItemOption o SET o.selected = false WHERE o.IIID = :iiid")
                .setParameter("iiid", IIID)
                .executeUpdate();
    }
}
