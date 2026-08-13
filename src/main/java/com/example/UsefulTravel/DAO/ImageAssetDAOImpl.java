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
}
