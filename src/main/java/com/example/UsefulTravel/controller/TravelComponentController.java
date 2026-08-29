package com.example.UsefulTravel.controller;

import com.example.UsefulTravel.DAO.ItineraryComponentDAO;
import com.example.UsefulTravel.DAO.TravelComponentDAO;
import com.example.UsefulTravel.entity.ItineraryComponent;
import com.example.UsefulTravel.entity.TravelComponent;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Controller
public class TravelComponentController {

    private final TravelComponentDAO travelComponentDAO;
    private final ItineraryComponentDAO itineraryComponentDAO;

    // 元件類型的固定分類：使用者要求把「遊覽車、導遊、保險」這類每次出團都需要的固定資源
    // 也建成標準元件，跟原本的航班/餐食/住宿等級/自費項目放在同一個下拉選單裡。
    // 用 LinkedHashMap 保留順序，「新增元件」下拉選單、「編輯」下拉選單、列表的分類標籤
    // 三個地方都直接讀這份清單，之後要再加新分類只需要改這裡一個地方。
    private static final Map<String, String> TYPE_LABELS = new LinkedHashMap<>();
    static {
        TYPE_LABELS.put("flight", "航班");
        TYPE_LABELS.put("bus", "遊覽車／包車");
        TYPE_LABELS.put("guide", "導遊／領隊");
        TYPE_LABELS.put("insurance", "保險");
        TYPE_LABELS.put("meal", "餐食");
        TYPE_LABELS.put("hotel_grade", "住宿等級");
        TYPE_LABELS.put("ticket", "門票／入場費");
        TYPE_LABELS.put("optional_tour", "自費項目");
        TYPE_LABELS.put("other", "其他固定項目");
    }

    @Autowired
    public TravelComponentController(TravelComponentDAO travelComponentDAO, ItineraryComponentDAO itineraryComponentDAO) {
        this.travelComponentDAO = travelComponentDAO;
        this.itineraryComponentDAO = itineraryComponentDAO;
    }

    // GET /component → 公司元件庫列表 (航班/遊覽車/導遊/保險/餐食/住宿/自費項目...固定資源)
    @GetMapping("/component")
    public String list(HttpSession session, Model model) {
        Integer AID = (Integer) session.getAttribute("AID");
        if (AID == null) return "redirect:/login";

        model.addAttribute("components", travelComponentDAO.findByAgency(AID));
        model.addAttribute("componentTypeLabels", TYPE_LABELS);
        return "component/list";
    }

    // POST /component/new → 新增一筆元件
    @PostMapping("/component/new")
    public String create(@RequestParam String type,
                          @RequestParam String name,
                          @RequestParam(required = false) BigDecimal defaultPrice,
                          @RequestParam(required = false) String description,
                          @RequestParam(required = false, defaultValue = "PER_PAX") String costType,
                          HttpSession session) {
        Integer AID = (Integer) session.getAttribute("AID");
        if (AID == null) return "redirect:/login";

        TravelComponent component = new TravelComponent(AID, type, name, defaultPrice, description);
        component.setCostType(costType);
        travelComponentDAO.save(component);
        return "redirect:/component";
    }

    // POST /component/{id}/edit → 編輯既有元件的內容 (類型/名稱/預設單價/計費方式/說明)
    @PostMapping("/component/{id}/edit")
    public String edit(@PathVariable("id") int CPID,
                        @RequestParam String type,
                        @RequestParam String name,
                        @RequestParam(required = false) BigDecimal defaultPrice,
                        @RequestParam(required = false) String description,
                        @RequestParam(required = false, defaultValue = "PER_PAX") String costType,
                        HttpSession session,
                        RedirectAttributes redirectAttributes) {
        Integer AID = (Integer) session.getAttribute("AID");
        if (AID == null) return "redirect:/login";

        TravelComponent component = travelComponentDAO.findById(CPID);
        if (component == null || component.getAID() != AID) {
            redirectAttributes.addFlashAttribute("deleteError", "找不到這個元件，或這個元件不屬於你的旅行社。");
            return "redirect:/component";
        }
        component.setType(type);
        component.setName(name);
        component.setDefaultPrice(defaultPrice);
        component.setDescription(description);
        component.setCostType(costType);
        travelComponentDAO.save(component);
        return "redirect:/component";
    }

    // POST /component/{id}/delete → 刪除元件
    // 如果這個元件已經被掛在某個行程的「報價元件」上，先擋下來請使用者去那個行程移除，
    // 避免行程上突然出現一個查不到來源的元件；報價單明細上的參照標記則可以安全清空
    // (實際金額已經複製存在明細裡，不影響已存在的報價)。
    @PostMapping("/component/{id}/delete")
    public String delete(@PathVariable("id") int CPID, HttpSession session, RedirectAttributes redirectAttributes) {
        Integer AID = (Integer) session.getAttribute("AID");
        if (AID == null) return "redirect:/login";

        TravelComponent component = travelComponentDAO.findById(CPID);
        if (component == null || component.getAID() != AID) {
            redirectAttributes.addFlashAttribute("deleteError", "找不到這個元件，或這個元件不屬於你的旅行社。");
            return "redirect:/component";
        }

        long usage = travelComponentDAO.countItineraryUsage(CPID);
        if (usage > 0) {
            redirectAttributes.addFlashAttribute("deleteError",
                    "這個元件目前掛在 " + usage + " 個行程的「報價元件」上，請先到那些行程移除後再刪除。");
            return "redirect:/component";
        }

        travelComponentDAO.clearQuotationLineReferences(CPID);
        travelComponentDAO.deleteById(CPID);
        return "redirect:/component";
    }

    // GET /itinerary/{id}/components → 這個行程目前掛的報價元件
    @GetMapping("/itinerary/{id}/components")
    public String itineraryComponents(@PathVariable("id") int ITID, HttpSession session, Model model) {
        Integer AID = (Integer) session.getAttribute("AID");
        if (AID == null) return "redirect:/login";

        model.addAttribute("itineraryId", ITID);
        model.addAttribute("attached", itineraryComponentDAO.findByItinerary(ITID));
        model.addAttribute("allComponents", travelComponentDAO.findByAgency(AID));
        return "component/itinerary-components";
    }

    // POST /itinerary/{id}/components → 把元件掛到這個行程 (用於 B2B 報價表)
    @PostMapping("/itinerary/{id}/components")
    public String attach(@PathVariable("id") int ITID,
                          @RequestParam int componentId,
                          @RequestParam(defaultValue = "1") int quantity,
                          @RequestParam(required = false) BigDecimal priceOverride) {
        itineraryComponentDAO.save(new ItineraryComponent(ITID, componentId, null, quantity, priceOverride));
        return "redirect:/itinerary/" + ITID + "/components";
    }
}
