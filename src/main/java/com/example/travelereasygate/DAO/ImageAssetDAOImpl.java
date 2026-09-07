package com.example.travelereasygate.DAO;

import com.example.travelereasygate.entity.ImageAsset;
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

    // 同一個 PID (尤其是共用庫景點) 可能被多間旅行社各自上傳圖片, 一定要用 AID 篩選,
    // 不然會把別間旅行社上傳、綁在同一個共用景點上的照片也顯示出來 (跨租戶資料外洩)。
    @Override
    public List<ImageAsset> findByPoi(int PID, int AID) {
        return em.createQuery(
                "SELECT i FROM ImageAsset i WHERE i.matchedPid = :pid AND i.AID = :aid ORDER BY i.createdAt DESC", ImageAsset.class)
                .setParameter("pid", PID)
                .setParameter("aid", AID)
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

    // PoiService.overrideSharedPoi() 呼叫: 這間旅行社改寫共用庫景點、產生專屬複本時, 把這間旅行社
    // 「自己」原本綁在共用庫舊 PID 上的圖片一併搬到新複本 PID 上 (用 AID 篩選, 只搬這間旅行社自己
    // 的圖片, 不會動到別間旅行社剛好也對同一筆共用庫景點上傳過、綁在同一個舊 PID 上的照片)。
    // 沒有這一步的話, 圖片本身還是「綁定成功」(matched_pid 有值), 但因為指到的是一筆現在已經被
    // 隱藏起來的共用庫景點, 「景點編輯頁」改用新複本 PID 去查自己的綁定圖片時會完全找不到, 使用者
    // 看到的就是「圖片資源庫顯示已綁定, 但景點編輯頁顯示尚未綁定」這種矛盾狀態。
    @Override
    @Transactional
    public void reassignMatchedPid(int oldPid, int newPid, int AID) {
        em.createQuery("UPDATE ImageAsset i SET i.matchedPid = :newPid WHERE i.matchedPid = :oldPid AND i.AID = :aid")
                .setParameter("newPid", newPid)
                .setParameter("oldPid", oldPid)
                .setParameter("aid", AID)
                .executeUpdate();
    }
}
