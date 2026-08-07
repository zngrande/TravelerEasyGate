package com.example.UsefulTravel.DAO;

import com.example.UsefulTravel.entity.AiParsedItem;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class AiParsedItemDAOImpl implements AiParsedItemDAO {

    private final EntityManager em;

    @Autowired
    public AiParsedItemDAOImpl(EntityManager em) {
        this.em = em;
    }

    @Override
    @Transactional
    public void save(AiParsedItem item) {
        if (item.getAPIID() == 0) {
            em.persist(item);
        } else {
            em.merge(item);
        }
    }

    @Override
    public AiParsedItem findById(int APIID) {
        return em.find(AiParsedItem.class, APIID);
    }

    @Override
    public List<AiParsedItem> findByDay(int APDID) {
        return em.createQuery(
                "SELECT i FROM AiParsedItem i WHERE i.APDID = :apdid ORDER BY i.sortOrder ASC", AiParsedItem.class)
                .setParameter("apdid", APDID)
                .getResultList();
    }
}
