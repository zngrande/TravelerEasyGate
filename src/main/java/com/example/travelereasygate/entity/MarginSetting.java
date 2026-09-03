package com.example.travelereasygate.entity;

import com.example.travelereasygate.service.FormulaEngine;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "margin_setting")
public class MarginSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MSID")
    private int MSID;

    @Column(name = "AID")
    private int AID;

    @Column(name = "name")
    private String name;

    @Column(name = "trade_markup_pct")
    private BigDecimal tradeMarkupPct = BigDecimal.ZERO; // 同業加成%

    @Column(name = "retail_markup_pct")
    private BigDecimal retailMarkupPct = BigDecimal.ZERO; // 直售加成%

    @Column(name = "rebate_pct")
    private BigDecimal rebatePct = BigDecimal.ZERO; // 退傭% (舊制% —— 保留給還沒轉成公式的舊資料 fallback 用)

    // ------------------------------------------------------------
    // 計算公式 (取代上面三個 %欄位): 用價格變數 + 運算子組成的運算式字串,
    // 例如 "{NET_COST} * 1.15 + 2000"。這三個欄位 nullable，空白代表這組規則
    // 還是舊制 %，QuotationService 算價錢時會自動 fallback 回上面的 %欄位。
    // ------------------------------------------------------------
    @Column(name = "trade_formula", length = 500)
    private String tradeFormula; // 同業價公式, 可用變數: {GROSS_COST} {NET_COST} {GROUP_SIZE}

    @Column(name = "retail_formula", length = 500)
    private String retailFormula; // 直售價公式, 可用變數: {GROSS_COST} {NET_COST} {GROUP_SIZE} {TRADE_PRICE}

    @Column(name = "rebate_formula", length = 500)
    private String rebateFormula; // 退傭金額公式, 可用變數: {GROSS_COST} {NET_COST} {GROUP_SIZE} {TRADE_PRICE}

    // 基本報價公式 —— 使用者回報「①基本報價公式一直沒辦法存到計算公式管理」: 這組規則以前的設計是
    // 「基本報價沒有對應的已儲存規則, 一律讓每張報價單自己填」(因為 MarginSetting 從第一版就只管
    // 同業/直售/退傭三層); 現在補上這一層, 讓「已儲存的」規則也可以連基本報價一起存, 選規則時①②③④
    // 四層可以一次套用, 不用「已儲存的套②③④、①還要自己填」這樣拆兩邊維護。跟上面三個公式欄位一樣是
    // nullable: 沒填代表這組規則不管基本報價這一層, 套用時基本報價繼續沿用那張報價單自己的
    // basic_markup_mode/basic_markup_value 設定 (跟「這組規則沒填 retailFormula 時直售價視同 0」不同,
    // 基本報價沒有視同 0 這回事——完全沒有基本報價, 後面同業/直售就沒有東西可以疊加了)。
    @Column(name = "basic_formula", length = 500)
    private String basicFormula; // 基本報價公式, 可用變數: {GROSS_COST} {NET_COST} {GROUP_SIZE}

    // ------------------------------------------------------------
    // 「整團人數級距報價結果」卡片的 NP／團費成本計算式 —— 使用者要求這組也要能存到「計算公式管理」、
    // 報價單選「已儲存的」或「自填」。這兩個公式用的是完全不同的一組變數 (VARIABLE_COST/MISC/FIXED_GROUP/
    // NET/BASIC_PRICE/NP, 對應每一個「人數級距」的每人成本試算), 跟上面 basic/trade/retail/rebate 那組
    // (GROSS_COST/NET_COST/GROUP_SIZE/BASIC_PRICE/TRADE_PRICE/RETAIL_PRICE, 整單層級的總價) 是兩個獨立的
    // 命名空間, 只是剛好都有一個叫 BASIC_PRICE 的變數、意義相通 (基本報價), 兩邊分開驗證/計算不會互相干擾。
    // 一樣是 nullable: 沒填代表這組規則不管 NP／團費成本這一層, 套用時繼續沿用這張報價單「整團人數級距報價
    // 結果」卡片自己填的 (存在 QuotationGroupTier.npFormula/teamFormula, 「套用到全部級距」按鈕存的那組)。
    // ------------------------------------------------------------
    @Column(name = "np_formula", length = 500)
    private String npFormula; // NP 計算式, 可用變數: {VARIABLE_COST} {MISC} {FIXED_GROUP} {NET} {BASIC_PRICE}

    @Column(name = "team_formula", length = 500)
    private String teamFormula; // 團費成本計算式, 可用變數同上 + {NP}

    // 2026-09-03 修正: 原本一組規則只有一個 isDefault, 「報價定價規則」跟「NP／團費成本規則」
    // 沒辦法各自獨立設定自己的預設 (設定其中一種為預設會連帶影響另一種能不能維持預設)。拆成兩個
    // 獨立欄位, 一筆規則可以「報價定價是預設、NP/團費成本不是」, 也可以兩者都是 (混合規則),
    // 互不影響。見 db/migration_margin_setting_split_default.sql。
    @Column(name = "default_pricing")
    private boolean defaultPricing = false;

    @Column(name = "default_tier")
    private boolean defaultTier = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public MarginSetting() {}

    public MarginSetting(int AID, String name, BigDecimal tradeMarkupPct, BigDecimal retailMarkupPct, BigDecimal rebatePct) {
        this.AID = AID;
        this.name = name;
        this.tradeMarkupPct = tradeMarkupPct;
        this.retailMarkupPct = retailMarkupPct;
        this.rebatePct = rebatePct;
    }

    public MarginSetting(int AID, String name, String tradeFormula, String retailFormula, String rebateFormula) {
        this.AID = AID;
        this.name = name;
        this.tradeFormula = tradeFormula;
        this.retailFormula = retailFormula;
        this.rebateFormula = rebateFormula;
    }

    public int getMSID() { return MSID; }
    public void setMSID(int MSID) { this.MSID = MSID; }

    public int getAID() { return AID; }
    public void setAID(int AID) { this.AID = AID; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public BigDecimal getTradeMarkupPct() { return tradeMarkupPct; }
    public void setTradeMarkupPct(BigDecimal tradeMarkupPct) { this.tradeMarkupPct = tradeMarkupPct; }

    public BigDecimal getRetailMarkupPct() { return retailMarkupPct; }
    public void setRetailMarkupPct(BigDecimal retailMarkupPct) { this.retailMarkupPct = retailMarkupPct; }

    public BigDecimal getRebatePct() { return rebatePct; }
    public void setRebatePct(BigDecimal rebatePct) { this.rebatePct = rebatePct; }

    public String getTradeFormula() { return tradeFormula; }
    public void setTradeFormula(String tradeFormula) { this.tradeFormula = tradeFormula; }

    public String getRetailFormula() { return retailFormula; }
    public void setRetailFormula(String retailFormula) { this.retailFormula = retailFormula; }

    public String getRebateFormula() { return rebateFormula; }
    public void setRebateFormula(String rebateFormula) { this.rebateFormula = rebateFormula; }

    public String getBasicFormula() { return basicFormula; }
    public void setBasicFormula(String basicFormula) { this.basicFormula = basicFormula; }

    public String getNpFormula() { return npFormula; }
    public void setNpFormula(String npFormula) { this.npFormula = npFormula; }

    public String getTeamFormula() { return teamFormula; }
    public void setTeamFormula(String teamFormula) { this.teamFormula = teamFormula; }

    // 這組規則是不是「公式制」(只要基本/同業/直售/退傭四個公式欄位有任一個填了字就算) —— 給畫面顯示/邏輯
    // 判斷用。NP/團費成本刻意不算進這個判斷: 那兩個是完全獨立的「整團人數級距」子功能, 一組規則可以只管
    // 基本/同業/直售/退傭四層、完全不管 NP/團費成本 (或反過來), isFormulaMode() 這裡維持只看報價定價的四層,
    // 避免「舊制%」徽章的判斷邏輯被 NP/團費成本這種跟報價定價無關的欄位影響。
    @Transient
    public boolean isFormulaMode() {
        return notBlank(basicFormula) || notBlank(tradeFormula) || notBlank(retailFormula) || notBlank(rebateFormula);
    }

    // 這組規則是不是「純 NP／團費成本規則」(①②③④基本報價/同業/直售/退傭四個公式欄位全部空白, 但⑤⑥
    // NP/團費成本至少填了一個) —— 給「計算公式管理」清單頁判斷用, 避免這種規則被誤貼上「舊制%」徽章
    // (那個徽章的本意是「這組規則的報價定價還在用舊制百分比算法」, 但一組完全不管報價定價的純 NP/團費成本
    // 規則, 貼「舊制%」反而會誤導使用者以為它有在用百分比算報價)。
    @Transient
    public boolean isPureTierRule() {
        return !isFormulaMode() && (notBlank(npFormula) || notBlank(teamFormula));
    }

    // 這幾個公式欄位可以用到的變數 -> 使用者看得懂的中文說明, 跟 quotation/edit.html「基本報價／同業／
    // 直售加成」卡片的 VARIABLES 清單一一對應 (這組公式套用生效時, QuotationService.recalculateQuotationPricing()
    // 傳進去的 formulaVars 就是這幾個 key)。只給「唯讀顯示」轉換文字用, 不影響 FormulaEngine 實際計算。
    private static final Map<String, String> VARIABLE_LABELS = Map.of(
            "GROSS_COST", "Net（總成本）",
            "NET_COST", "NNet（總淨成本）",
            "GROUP_SIZE", "團體人數",
            "BASIC_PRICE", "基本報價",
            "TRADE_PRICE", "同業價",
            "RETAIL_PRICE", "直售價"
    );

    // 「整團人數級距報價結果」卡片 NP／團費成本計算式的變數說明, 跟上面 VARIABLE_LABELS 是分開的另一組
    // (對應 quotation/edit.html「整團人數級距報價結果」卡片的 gVarChips 清單), 給 readable 版本轉換用。
    private static final Map<String, String> TIER_VARIABLE_LABELS = Map.of(
            "VARIABLE_COST", "每人變動成本",
            "MISC", "雜項（全團固定費用）",
            "FIXED_GROUP", "全團固定（不含額外雜項，僅自動加總）",
            "NET", "NET（每人 Net，原始牌價）",
            "BASIC_PRICE", "基本報價",
            "NP", "NP"
    );

    // 給畫面「唯讀顯示」用的公式文字 (「計算公式管理」清單頁的公式徽章、報價單「選擇計算方式」下拉選單摘要都
    // 用這幾個, 不要直接顯示 basicFormula/tradeFormula/retailFormula/rebateFormula 這幾個原始欄位——原始欄位
    // 是存給 FormulaEngine 算式解析器讀的, 裡面的 {NET_COST} 這種大括號變數語法只有系統看得懂, 直接顯示給
    // 使用者看會變成一串看不懂的 "{NET_COST}*1.15"。這幾個 readable 版本才是要給人看的, 值是 null 時代表
    // 這一層還是舊制百分比 (或, 基本報價這一層, 代表沿用報價單自己的設定), 呼叫端要自己判斷 fallback。
    @Transient
    public String getReadableBasicFormula() { return FormulaEngine.toReadable(basicFormula, VARIABLE_LABELS); }

    @Transient
    public String getReadableTradeFormula() { return FormulaEngine.toReadable(tradeFormula, VARIABLE_LABELS); }

    @Transient
    public String getReadableRetailFormula() { return FormulaEngine.toReadable(retailFormula, VARIABLE_LABELS); }

    @Transient
    public String getReadableRebateFormula() { return FormulaEngine.toReadable(rebateFormula, VARIABLE_LABELS); }

    @Transient
    public String getReadableNpFormula() { return FormulaEngine.toReadable(npFormula, TIER_VARIABLE_LABELS); }

    @Transient
    public String getReadableTeamFormula() { return FormulaEngine.toReadable(teamFormula, TIER_VARIABLE_LABELS); }

    // 給下拉選單/清單用的一行摘要, 公式制顯示「看得懂的公式文字」(不是原始的 {VAR_ID} 語法), 舊制% 就顯示百分比,
    // 兩種資料可以混著出現不會壞畫面。基本報價這一層沒有舊制%可以顯示 (MarginSetting 從來沒有 basicMarkupPct
    // 這個欄位), 沒填就顯示「沿用報價單設定」。
    @Transient
    public String getSummary() {
        String basic = notBlank(basicFormula) ? getReadableBasicFormula() : "基本報價沿用報價單設定";
        String trade = notBlank(tradeFormula) ? getReadableTradeFormula() : ("同業+" + tradeMarkupPct + "%");
        String retail = notBlank(retailFormula) ? getReadableRetailFormula() : ("直售+" + retailMarkupPct + "%");
        String rebate = notBlank(rebateFormula) ? getReadableRebateFormula() : ("退傭" + rebatePct + "%");
        return name + "（" + basic + " / " + trade + " / " + retail + " / " + rebate + "）";
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }

    public boolean isDefaultPricing() { return defaultPricing; }
    public void setDefaultPricing(boolean defaultPricing) { this.defaultPricing = defaultPricing; }

    public boolean isDefaultTier() { return defaultTier; }
    public void setDefaultTier(boolean defaultTier) { this.defaultTier = defaultTier; }

    // 給畫面顯示「這筆規則有沒有掛著任一種預設」用 (例如一般判斷式), 實際個別判斷還是要看
    // isDefaultPricing()/isDefaultTier() 分開的值。
    @Transient
    public boolean isAnyDefault() { return defaultPricing || defaultTier; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
