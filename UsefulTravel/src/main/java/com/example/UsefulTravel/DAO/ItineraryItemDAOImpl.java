package com.example.UsefulTravel.DAO;

import com.example.UsefulTravel.entity.ItineraryItem;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ItineraryItemDAOImpl implements ItineraryItemDAO {

    private final EntityManager em;

    @Autowired
    public ItineraryItemDAOImpl(EntityManager em) {
        this.em = em;
    }

    @Override
    @Transactional
    public void save(ItineraryItem item) {
        if (item.getIIID() == 0) {
            em.persist(item);
        } else {
            em.merge(item);
        }
    }

    @Override
    public ItineraryItem findById(int IIID) {
        return em.find(ItineraryItem.class, IIID);
    }

    @Override
    public List<ItineraryItem> findByDay(int IDID) {
        return em.createQuery(
                "SELECT it FROM ItineraryItem it WHERE it.IDID = :idid ORDER BY it.sortOrder ASC", ItineraryItem.class)
                .setParameter("idid", IDID)
                .getResultList();
    }

    @Override
    @Transactional
    public void deleteById(int IIID) {
        ItineraryItem item = em.find(ItineraryItem.class, IIID);
        if (item != null) {
            em.remove(item);
        }
    }

    // 拖曳排序後更新單一項目的 sort_order (前端會針對整批項目呼叫這個方法)
    @Override
    @Transactional
    public void updateSortOrder(int IIID, int sortOrder) {
        ItineraryItem item = em.find(ItineraryItem.class, IIID);
        if (item != null) {
            item.setSortOrder(sortOrder);
            em.merge(item);
        }
    }
}
