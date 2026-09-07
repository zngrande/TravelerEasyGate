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
     * 刪除 POI 前, 先解除所有指向它的外鍵參照 (行程項目、AI 解析暫存項目、圖片綁定),
     * 不然 itinerary_item.PID / ai_parsed_item.matched_pid / image_asset.matched_pid 這幾個
     * 外鍵沒設 CASCADE 會直接擋住刪除。
     *
     * 使用者反映「新增完圖片、修改景點 (產生「修改後景點」複本)、再刪除修改後景點, 圖片就不會顯示
     * 也變成未綁定」——追查後發現: 這裡刪除的其實是 overrideSharedPoi() 產生出來的專屬複本, 使用者
     * 刪除的意圖比較接近「放棄我自己這份修改, 改回用共用庫原本的版本」, 不是「這個景點 (連同共用庫
     * 原始那筆) 整個都不要了」。舊的做法不分青紅皂白地把所有參照都清空/解除綁定, 對複本來說等於把
     * 使用者原本已經好好綁定在共用庫景點上的圖片/行程項目憑空弄丟, 而且共用庫原始那筆也會因為
     * PoiOverride 紀錄還留著而永遠對這間旅行社隱藏, 使用者從此再也找不回這筆共用庫景點。
     *
     * 修正: 刪除前先檢查這個 PID 是不是這間旅行社某筆 PoiOverride 紀錄的複本 (overridePid)。如果是,
     * 把所有參照 (image_asset.matched_pid / itinerary_item.PID / ai_parsed_item.matched_pid) 從
     * 複本 PID 改回共用庫原始 PID, 並移除這筆 override 紀錄本身 (讓共用庫原始那筆重新對這間旅行社
     * 可見), 而不是清空/解除綁定——這樣圖片、已排進行程的項目都會自動「回到」共用庫原始版本, 使用者
     * 不會發現東西不見了。只有真正「這間旅行社自己新增、不是任何共用庫景點複本」的景點, 才會走原本
     * 的清空/解除綁定流程 (這種情況沒有共用庫原始版本可以回退, 刪除就是真的刪除)。
     */
    public void delete(int AID, int PID) {
        PoiOverride override = poiOverrideDAO.findByAgencyAndOverridePid(AID, PID);
        if (override != null) {
            int originalPid = override.getOriginalPid();
            itineraryItemDAO.reassignPidReferences(PID, originalPid);
            aiParsedItemDAO.reassignMatchedPid(PID, originalPid);
            imageAssetDAO.reassignMatchedPid(PID, originalPid, AID);
            poiOverrideDAO.delete(override);
        } else {
            itineraryItemDAO.clearPidReferences(PID);
            aiParsedItemDAO.clearMatchedPid(PID);
            imageAssetDAO.clearMatchedPid(PID);
        }
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

        // 使用者反映「景點編輯頁的綁定圖片顯示尚未綁定, 但圖片資源庫明明顯示已綁定」——根因跟圖片
        // 資源庫那個「已綁定景點顯示空白」是同一個問題的另一種表現: 這間旅行社原本已經綁在「共用庫
        // 舊 PID」上的圖片 (image_asset.matched_pid), 在上面複製出專屬複本 (新 PID) 的當下並沒有
        // 跟著搬過去, 圖片還是指著舊 PID; 而景點編輯頁 (PoiController) 是直接拿「目前正在編輯的
        // PID」(這裡是新複本) 去查自己的綁定圖片 (ImageAssetDAO.findByPoi), 舊 PID 上的圖片當然
        // 完全查不到, 顯示就變成「尚未綁定」——即使圖片本身的綁定其實是成功的。
        // 這裡把這間旅行社自己的圖片一併搬到新複本 PID, 之後不管是圖片資源庫還是景點編輯頁都能正確
        // 找到同一批圖片。已經在這次修正部署「之前」就發生過的 override, 資料庫裡的舊資料需要另外
        // 補跑一次性的資料修正 (見 db/migration V3), 這裡的程式碼修正只保證「以後」不會再發生。
        imageAssetDAO.reassignMatchedPid(original.getPID(), edited.getPID(), AID);

        return edited;
    }

    /**
     * 給 ImageAssetService 用: 把一個「可能已經過期」的 PID 轉換成這間旅行社「目前實際對應」的 PID。
     *
     * 背景: 行程項目 (itinerary_item.PID) 綁定景點的當下, 這個景點可能還沒被這間旅行社 override
     * 過; 之後這間旅行社在別的地方 (例如「景點管理」頁, 或透過另一個行程項目編輯介紹說明)
     * override 了同一筆共用庫景點, 但這個行程項目自己的 PID 欄位不會跟著自動更新 (只有
     * updateDescription() 那個特定入口才會順便更新「觸發那次操作的那一個」項目, 不會處理
     * 所有指到同一個原始 PID 的其他項目)。如果從行程看板對這個項目上傳/查詢圖片, 用的就會是
     * 這個「舊的、現在已經被隱藏的」PID, 跟圖片資源庫/景點編輯頁看到的 (新複本 PID) 對不起來。
     *
     * 這裡在每次上傳/查詢圖片綁定前都先轉換一次: 如果傳進來的 PID 剛好是「這間旅行社已經
     * override 過的某筆共用庫原始 PID」, 就回傳對應的複本 PID; 否則原樣傳回 (代表這個 PID
     * 目前對這間旅行社來說本來就是正確、可見的版本, 不需要轉換, 包含私有景點、共用庫裡還沒被
     * 任何人 override 過的景點, 或本來就已經是複本 PID 的情況)。
     */
    public int resolveCurrentPid(int AID, int PID) {
        PoiOverride override = poiOverrideDAO.findByAgencyAndOriginal(AID, PID);
        if (override != null && override.getOverridePid() != null) {
            return override.getOverridePid();
        }
        return PID;
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
