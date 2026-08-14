package com.example.UsefulTravel.DAO;

import com.example.UsefulTravel.entity.ImageAsset;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class ImageAssetDAOImpl implements ImageAssetDAO {

    private final EntityManager em;

    @Autowired
    public ImageAssetDAOImpl(EntityManager em) {
        this.em = em;
    }

    @Override
    @Transactional
    public void save(ImageAsset image) {
        if (image.getCreatedAt() == null) {
            image.setCreatedAt(LocalDateTime.now());
        }
        if (image.getIAID() == 0) {
            em.persist(image);
        } else {
            em.merge(image);
        }
    }

    @Override
    public ImageAsset findById(int IAID) {
        return em.find(ImageAsset.class, IAID);
    }

    @Override
    public List<ImageAsset> findByAgency(int AID) {
        return em.createQuery(
                "SELECT i FROM ImageAsset i WHERE i.AID = :aid ORDER BY i.createdAt DESC", ImageAsset.class)
                .setParameter("aid", AID)
                .getResultList();
    }

    @Override
    public List<ImageAsset> findByPoi(int PID) {
        return em.createQuery(
                "SELECT i FROM ImageAsset i WHERE i.matchedPid = :pid ORDER BY i.createdAt DESC", ImageAsset.class)
                .setParameter("pid", PID)
                .getResultList();
    }

    @Override
    public List<ImageAsset> findUnlinked(int AID) {
        return em.createQuery(
                "SELECT i FROM ImageAsset i WHERE i.AID = :aid AND i.matchedPid IS NULL ORDER BY i.createdAt DESC",
                ImageAsset.class)
                .setParameter("aid", AID)
                .getResultList();
    }

    @Override
    @Transactional
    public void deleteById(int IAID) {
        ImageAsset image = em.find(ImageAsset.class, IAID);
        if (image != null) {
            em.remove(image);
        }
    }

    // 刪除 POI 前呼叫: 把所有綁定到這個 POI 的圖片解除綁定 (圖片不會被刪, 只是變回未綁定)
    @Override
    @Transactional
    public void clearMatchedPid(int PID) {
        em.createQuery("UPDATE ImageAsset i SET i.matchedPid = NULL WHERE i.matchedPid = :pid")
                .setParameter("pid", PID)
                .executeUpdate();
    }
}
