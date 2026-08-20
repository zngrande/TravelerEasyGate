package com.example.UsefulTravel.service;

import com.example.UsefulTravel.DAO.AiParsedItemDAO;
import com.example.UsefulTravel.DAO.ImageAssetDAO;
import com.example.UsefulTravel.DAO.ItineraryItemDAO;
import com.example.UsefulTravel.DAO.PoiDAO;
import com.example.UsefulTravel.entity.Poi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PoiService {

    private final PoiDAO poiDAO;
    private final ItineraryItemDAO itineraryItemDAO;
    private final AiParsedItemDAO aiParsedItemDAO;
    private final ImageAssetDAO imageAssetDAO;

    @Autowired
    public PoiService(PoiDAO poiDAO, ItineraryItemDAO itineraryItemDAO,
                      AiParsedItemDAO aiParsedItemDAO, ImageAssetDAO imageAssetDAO) {
        this.poiDAO = poiDAO;
        this.itineraryItemDAO = itineraryItemDAO;
        this.aiParsedItemDAO = aiParsedItemDAO;
        this.imageAssetDAO = imageAssetDAO;
    }

    public void save(Poi poi) {
        poiDAO.save(poi);
    }

    /**
     * 只更新景點的介紹說明 (給行程編輯畫面用: 編輯已連結 POI 的項目時, 在底部顯示/修改介紹說明,
     * 存檔時直接同步回 POI 資料庫, 不影響這個景點的其他欄位)
     */
    public void updateDescription(int PID, String description) {
        Poi poi = poiDAO.findById(PID);
        if (poi == null) throw new IllegalArgumentException("找不到這個景點");
        poi.setDescription(description);
        poiDAO.save(poi);
    }

    public Poi findById(int PID) {
        return poiDAO.findById(PID);
    }

    public List<Poi> listForAgency(int AID) {
        return poiDAO.findByAgencyOrShared(AID);
    }

    public List<Poi> listForItinerary(int AID, String country, String region) {
        return poiDAO.findByAgencyAndCountry(AID, country, region);
    }

    public List<Poi> search(int AID, String keyword, String category) {
        return poiDAO.searchByKeyword(AID, keyword, category);
    }

    /**
     * 刪除 POI 前, 先解除所有指向它的外鍵參照 (行程項目、AI 解析暫存項目),
     * 不然 itinerary_item.PID / ai_parsed_item.matched_pid 這兩個外鍵沒設 CASCADE 會直接擋住刪除。
     * 已經排進行程的項目不會消失, 只是變回「未連結資料庫」的自訂項目 (名稱/座標都還在)。
     */
    public void delete(int PID) {
        itineraryItemDAO.clearPidReferences(PID);
        aiParsedItemDAO.clearMatchedPid(PID);
        imageAssetDAO.clearMatchedPid(PID);
        poiDAO.deleteById(PID);
    }
}
