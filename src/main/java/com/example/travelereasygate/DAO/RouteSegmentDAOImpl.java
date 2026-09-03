package com.example.travelereasygate.DAO;

import com.example.travelereasygate.entity.RouteSegment;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class RouteSegmentDAOImpl implements RouteSegmentDAO {

    private final EntityManager em;

    @Autowired
    public RouteSegmentDAOImpl(EntityManager em) {
        this.em = em;
    }

    @Override
    @Transactional
    public void save(RouteSegment segment) {
        if (segment.getCalculatedAt() == null) {
            segment.setCalculatedAt(LocalDateTime.now());
        }
        em.persist(segment);
    }

    @Override
    @Transactional
    public void update(RouteSegment segment) {
        em.merge(segment);
    }

    @Override
    public List<RouteSegment> findByDay(int IDID) {
        return em.createQuery(
                "SELECT r FROM RouteSegment r WHERE r.IDID = :idid ORDER BY r.RSID ASC", RouteSegment.class)
                .setParameter("idid", IDID)
                .getResultList();
    }

    // 重新排序後, 舊的路段快取要整批清掉再重算
    @Override
    @Transactional
    public void deleteByDay(int IDID) {
        em.createQuery("DELETE FROM RouteSegment r WHERE r.IDID = :idid")
                .setParameter("idid", IDID)
                .executeUpdate();
    }

    @Override
    public RouteSegment findById(int RSID) {
        return em.find(RouteSegment.class, RSID);
    }

    @Override
    @Transactional
    public void updateTransportMode(int RSID, String mode) {
        RouteSegment seg = em.find(RouteSegment.class, RSID);
        if (seg != null) {
            seg.setTransportMode(mode);
            em.merge(seg);
        }
    }
}
