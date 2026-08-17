package com.example.UsefulTravel.DAO;

import com.example.UsefulTravel.entity.TravelComponent;

import java.util.List;

public interface TravelComponentDAO {
    void save(TravelComponent component);
    TravelComponent findById(int CPID);
    List<TravelComponent> findByAgency(int AID);
    void deleteById(int CPID);

    // 這個元件有沒有已經被實際掛在某個行程上 (itinerary_component.CPID 是 NOT NULL, 沒辦法單純清空,
    // 只能阻擋刪除, 請使用者自己先去把行程上的元件移除)
    long countItineraryUsage(int CPID);

    // 報價單明細上的 CPID 只是「這筆明細當初是從哪個元件複製出來的」參考標記 (單價等實際金額已經複製存在明細裡了,
    // 不影響已存在的報價單金額), 刪除元件時可以安全地把這個參考清空, 不需要因此擋住刪除
    void clearQuotationLineReferences(int CPID);
}