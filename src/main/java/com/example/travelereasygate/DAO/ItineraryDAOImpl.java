package com.example.travelereasygate.DAO;

import com.example.travelereasygate.entity.Itinerary;
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
    @Transactional
    public void updatePinnedState(int ITID, boolean pinned, LocalDateTime pinnedAt) {
        // 用 em.find() + setter + em.merge() (跟這個專案其他「只改一兩個欄位」的 DAO 方法, 例如
        // ItineraryItemDAOImpl.updateSortOrder()/RouteSegmentDAOImpl.updateTransportMode() 同一套寫法),
        // 刻意不呼叫 save() —— 那個方法每次都會把 updated_at 蓋成現在時間, 就是「取消釘選後不會跑回
        // 原本位置」的根因。這裡完全不去動 updatedAt 這個欄位, 讓它維持原本實際內容更新的時間。
        Itinerary itinerary = em.find(Itinerary.class, ITID);
        if (itinerary == null) return;
        itinerary.setPinned(pinned);
        itinerary.setPinnedAt(pinnedAt);
        em.merge(itinerary);
    }

    @Override
    public Itinerary findById(int ITID) {
        return em.find(Itinerary.class, ITID);
    }

    @Override
    public List<Itinerary> findByAgency(int AID) {
        // 釘選的行程 (pinned=true) 一律排在最前面, 同樣是釘選的話最近釘選的排前面；
        // 沒釘選的行程維持原本「最近更新排前面」的排序, 最後加上 ITID DESC 當第三層 tiebreaker——
        // 測試/種子資料常常一批建立時 updated_at 精確到秒都一樣, 沒有明確 tiebreaker 的話要不要顯示
        // 在前面完全看資料庫回傳的隨機順序 (實務上常常巧合等於 ITID 順序, 但不保證), 明確加上 ITID DESC
        // 讓「時間戳記打平時, 較新建立的排前面」變成穩定、看得懂的規則, 不是隱含的巧合。
        return em.createQuery(
                "SELECT i FROM Itinerary i WHERE i.AID = :aid " +
                        "ORDER BY i.pinned DESC, i.pinnedAt DESC, i.updatedAt DESC, i.ITID DESC", Itinerary.class)
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
