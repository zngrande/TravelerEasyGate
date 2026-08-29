package com.example.UsefulTravel.DAO;

import com.example.UsefulTravel.entity.Itinerary;

import java.time.LocalDateTime;
import java.util.List;

public interface ItineraryDAO {
    void save(Itinerary itinerary);
    Itinerary findById(int ITID);
    List<Itinerary> findByAgency(int AID);
    void deleteById(int ITID);

    // 釘選/取消釘選: 直接用 UPDATE 語句只改 pinned/pinned_at 這兩欄, 刻意不走 save() (那個每次都會
    // 把 updated_at 蓋成現在時間) —— 使用者回報「取消釘選後, 行程不會跑回原本的位置」, 根因就是 save()
    // 這個副作用: 取消釘選時 updated_at 被順便改成「取消釘選當下」, 讓這個行程在 findByAgency() 的
    // 「沒釘選的話依 updated_at DESC 排序」規則下排到最上面, 而不是回到它原本依實際內容更新時間該在的位置。
    void updatePinnedState(int ITID, boolean pinned, LocalDateTime pinnedAt);
}
