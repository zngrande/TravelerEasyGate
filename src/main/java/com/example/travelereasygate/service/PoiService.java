package com.example.travelereasygate.service;

import com.example.travelereasygate.DAO.AiParsedItemDAO;
import com.example.travelereasygate.DAO.ImageAssetDAO;
import com.example.travelereasygate.DAO.ItineraryItemDAO;
import com.example.travelereasygate.DAO.PoiDAO;
import com.example.travelereasygate.DAO.PoiOverrideDAO;
import com.example.travelereasygate.entity.ItineraryItem;
import com.example.travelereasygate.entity.Poi;
import com.example.travelereasygate.entity.PoiOverride;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PoiService {

    private final PoiDAO poiDAO;
    private final PoiOverrideDAO poiOverrideDAO;
    private final ItineraryItemDAO itineraryItemDAO;
    private final AiParsedItemDAO aiParsedItemDAO;
    private final ImageAssetDAO imageAssetDAO;

    @Autowired
    public PoiService(PoiDAO poiDAO, PoiOverrideDAO poiOverrideDAO, ItineraryItemDAO itineraryItemDAO,
                      AiParsedItemDAO aiParsedItemDAO, ImageAssetDAO imageAssetDAO) {
        this.poiDAO = poiDAO;
        this.poiOverrideDAO = poiOverrideDAO;
        this.itineraryItemDAO = itineraryItemDAO;
        this.aiParsedItemDAO = aiParsedItemDAO;
        this.imageAssetDAO = imageAssetDAO;
    }

    public void save(Poi poi) {
        poiDAO.save(poi);
    }

    /**
     * 只更新景點的介紹說明 (給行程編輯畫面用: 編輯已連結 POI 的項目時, 在底部顯示/修改介紹說明,
     * 存檔時直接同步回 POI 資料庫, 不影響這個景點的其他欄位)。
     *
     * 使用者要求: 這裡跟「編輯景點」頁面一樣要遵守共用庫的擁有權規則, 之前這個方法完全沒檢查 AID,
     * 直接改到傳進來的那個 PID, 等於從行程編輯畫面這個小捷徑就能繞過 Patch 10 剛做的共用庫保護。
     * 私有景點只能改自己的; 共用庫景點改的話跟「編輯景點」頁面一樣走複製流程 (不直接改共用庫本身),
     * 並且如果知道是哪個行程項目觸發的 (IIID), 順便把該項目的 PID 改指向新複本, 這樣這個項目之後
     * 看到的就是這間旅行社自己的版本, 不會又跑回共用庫原始那筆。
     *
     * @param IIID 觸發這次同步的行程項目 (選填, 沒有的話就不會嘗試改項目的連結)
     * @return 實際被更新的那筆 POI (私有景點就是原本那筆; 共用庫景點則是複本, PID 可能跟傳入的不同)
     */
    public Poi updateDescription(int AID, int PID, String description, Integer IIID) {
        Poi poi = poiDAO.findById(PID);
        if (poi == null) throw new IllegalArgumentException("找不到這個景點");
        if (poi.getAID() != null && !poi.getAID().equals(AID)) {
            throw new IllegalArgumentException("沒有權限編輯這筆景點");
        }

        if (poi.getAID() == null) {
            // 共用庫景點: 複製一份變成這間旅行社自己的, 介紹說明改在複本上 (共用庫原始那筆不會被動到)
            Poi copy = new Poi(AID, poi.getCategory(), poi.getName(), poi.getCountry(), poi.getCity(),
                    poi.getAddress(), poi.getLatitude(), poi.getLongitude());
            copy.setOriginalName(poi.getOriginalName());
            copy.setSuggestedStayMin(poi.getSuggestedStayMin());
            copy.setAgencyPrice(poi.getAgencyPrice());
            copy.setSupplierContact(poi.getSupplierContact());
            copy.setSupplierNotes(poi.getSupplierNotes());
            copy.setDescription(description);
            Poi saved = overrideSharedPoi(AID, poi, copy);

            if (IIID != null) {
                ItineraryItem item = itineraryItemDAO.findById(IIID);
                if (item != null && item.getPID() != null && item.getPID().equals(PID)) {
                    item.setPID(saved.getPID());
                    itineraryItemDAO.save(item);
                }
            }
            return saved;
        }

        poi.setDescription(description);
        poiDAO.save(poi);
        return poi;
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

    // poi/list.html「國家 / 城市」自動完成篩選欄位用: 在 keyword/category 的基礎上再多一個 location 條件
    public List<Poi> search(int AID, String keyword, String category, String location) {
        return poiDAO.searchByKeyword(AID, keyword, category, location);
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

    /**
     * 使用者要求: 公司改寫共用庫的景點時, 不能直接改到共用庫本身 (會影響其他旅行社),
     * 而是複製一份變成這間公司自己的景點, 並且記一筆 override, 讓這間公司之後看到的是自己的版本,
     * 共用庫原始那筆則不再顯示給這間公司 (其他旅行社完全不受影響, 還是看得到原始的共用庫資料)。
     *
     * @param AID      正在編輯的旅行社
     * @param original 共用庫裡原本那筆 (用來補上表單沒有的欄位, 例如 openHours/starRating)
     * @param edited   從編輯表單組出來、還沒存檔的新內容 (PID 應該是 0 或會被這裡強制重設成 0)
     * @return 新建立的專屬複本 (存檔後 PID 已填好)
     */
    public Poi overrideSharedPoi(int AID, Poi original, Poi edited) {
        edited.setAID(AID);
        edited.setOpenHours(original.getOpenHours());
        edited.setStarRating(original.getStarRating());

        // 萬一這間旅行社之前已經改寫過同一筆共用景點 (例如透過舊網址重新編輯到原始那筆),
        // 直接更新既有的複本, 不要每次都新增一筆造成孤兒資料
        PoiOverride override = poiOverrideDAO.findByAgencyAndOriginal(AID, original.getPID());
        if (override != null && override.getOverridePid() != null) {
            edited.setPID(override.getOverridePid());
        } else {
            edited.setPID(0); // 確保 save() 走 persist (新增), 不會誤更新到共用庫原本那筆
        }
        poiDAO.save(edited);

        if (override == null) {
            poiOverrideDAO.save(new PoiOverride(AID, original.getPID(), edited.getPID()));
        } else if (override.getOverridePid() == null) {
            // 之前是「隱藏」(沒有複本) 的紀錄, 這次變成有實際編輯內容: 補上複本 PID
            override.setOverridePid(edited.getPID());
            poiOverrideDAO.save(override);
        }
        return edited;
    }

    /**
     * 使用者「刪除」共用庫景點: 不會真的刪掉共用庫資料 (其他旅行社還是要看得到),
     * 只記一筆「這間旅行社選擇隱藏這筆」的 override (overridePid = null), 讓查詢時自動被排除。
     */
    public void hideSharedPoi(int AID, int originalPid) {
        PoiOverride override = poiOverrideDAO.findByAgencyAndOriginal(AID, originalPid);
        if (override == null) {
            poiOverrideDAO.save(new PoiOverride(AID, originalPid, null));
        }
    }

    // 給「建立新行程」頁面國家/地區自動完成用: 只回傳公司景點資料庫裡實際存在的國家/城市 (見 PoiDAOImpl 註解)
    public List<String> listCountries(int AID) {
        return poiDAO.findDistinctCountries(AID);
    }

    public List<String> listCitiesByCountry(int AID, String country) {
        return poiDAO.findDistinctCitiesByCountry(AID, country);
    }
}
