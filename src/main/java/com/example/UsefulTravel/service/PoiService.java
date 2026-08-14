package com.example.UsefulTravel.service;

import com.example.UsefulTravel.DAO.AiParsedItemDAO;
import com.example.UsefulTravel.DAO.ImageAssetDAO;
import com.example.UsefulTravel.DAO.ItineraryItemDAO;
import com.example.UsefulTravel.DAO.PoiCooperationLogDAO;
import com.example.UsefulTravel.DAO.PoiDAO;
import com.example.UsefulTravel.entity.Poi;
import com.example.UsefulTravel.entity.PoiCooperationLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class PoiService {

    private final PoiDAO poiDAO;
    private final PoiCooperationLogDAO poiCooperationLogDAO;
    private final ItineraryItemDAO itineraryItemDAO;
    private final AiParsedItemDAO aiParsedItemDAO;
    private final ImageAssetDAO imageAssetDAO;

    @Autowired
    public PoiService(PoiDAO poiDAO, PoiCooperationLogDAO poiCooperationLogDAO,
                      ItineraryItemDAO itineraryItemDAO, AiParsedItemDAO aiParsedItemDAO,
                      ImageAssetDAO imageAssetDAO) {
        this.poiDAO = poiDAO;
        this.poiCooperationLogDAO = poiCooperationLogDAO;
        this.itineraryItemDAO = itineraryItemDAO;
        this.aiParsedItemDAO = aiParsedItemDAO;
        this.imageAssetDAO = imageAssetDAO;
    }

    public void save(Poi poi) {
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
    // ---------------- 公司專屬資源庫: 合作紀錄 ----------------

    public void addCooperationLog(int PID, LocalDate logDate, String note, Integer createdBy) {
        poiCooperationLogDAO.save(new PoiCooperationLog(PID, logDate, note, createdBy));
    }

    public List<PoiCooperationLog> getCooperationLogs(int PID) {
        return poiCooperationLogDAO.findByPoi(PID);
    }
}
