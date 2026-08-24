package com.example.UsefulTravel.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "quotation")
public class Quotation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "QID")
    private int QID;

    @Column(name = "ITID")
    private int ITID;

    @Column(name = "AID")
    private int AID;

    @Column(name = "version")
    private int version = 1;

    @Column(name = "MSID")
    private Integer MSID;

    // 基本報價／同業／直售加成與退傭設定的模式: "preset" = 套用 MSID 指到的「計算公式管理」已儲存規則
    // (只影響同業/直售/退傭三層, 基本報價沒有對應的已儲存規則, 一律自己填); "custom" = 這張報價單自己填
    // (沿用下面 custom_*_formula / *_markup_mode / *_markup_value 這幾組舊欄位)。
    // 欄位本身沿用 migration_formula_pricing.sql 當時就建立好、DB 預設值是 'preset'、但一直沒接上畫面/邏輯的
    // formula_mode —— 因為 MSID 過去從來沒有被設定過, 這裡讀出來就算是 'preset' 也一定要另外檢查
    // MSID != null 才能真的套用已儲存規則, 否則舊資料 (MSID 一定是 null) 會被誤判成「已儲存規則」而算不出價錢,
    // 詳見 QuotationService#resolveActivePreset()。
    @Column(name = "formula_mode")
    private String formulaMode = "preset";

    @Column(name = "group_size")
    private int groupSize = 1;

    // 「整團人數級距報價結果」卡片的 NP／團費成本試算, 「雜項（全團固定費用）」要除以哪個代表人數:
    // LOWER=級距下限(保守) / AVERAGE=級距平均值 / UPPER=級距上限(樂觀)。開放區間 (沒有上限) 沒有平均值/上限
    // 可算, 一律退回用下限, 見 QuotationService#representativeHeadcountFor()。
    @Column(name = "group_tier_headcount_mode")
    private String groupTierHeadcountMode = "LOWER";

    // 完整鏈路 (需求文件最新版):
    //   Net 總成本 (原始牌價, 單價×數量) → NNet 總淨成本 (扣 FOC/折讓/返利)
    //     → 基本報價 (NNet + 基本利潤) → 總同業價 (基本報價 + 同業利潤) → 總直售價 (同業價 + 直售利潤)
    // 每一層利潤都是每張報價單自己獨立設定, 建立新版本時會從同一行程上一版報價單帶入初始值, 之後各自獨立可調
    // mode = PERCENT (依 % 數算, 乘上一層的金額) 或 AMOUNT (直接自填一個固定金額當作利潤)
    @Column(name = "basic_markup_mode")
    private String basicMarkupMode = "PERCENT";

    @Column(name = "basic_markup_value")
    private java.math.BigDecimal basicMarkupValue = java.math.BigDecimal.ZERO;

    @Column(name = "trade_markup_mode")
    private String tradeMarkupMode = "PERCENT";

    @Column(name = "trade_markup_value")
    private java.math.BigDecimal tradeMarkupValue = java.math.BigDecimal.ZERO;

    @Column(name = "retail_markup_mode")
    private String retailMarkupMode = "PERCENT";

    @Column(name = "retail_markup_value")
    private java.math.BigDecimal retailMarkupValue = java.math.BigDecimal.ZERO;

    // 退傭: 原本掛在「加成規則」範本上, 現在改成每張報價單自己獨立填, 邏輯跟基本/同業/直售加成放在同一個地方調
    // mode=PERCENT: 退傭金額 = 同業價 × rebatePct%; mode=AMOUNT: 退傭金額 = rebatePct 這個固定金額 (欄位名稱沿用, 當作數值用)
    @Column(name = "rebate_mode")
    private String rebateMode = "PERCENT";

    @Column(name = "rebate_pct")
    private java.math.BigDecimal rebatePct = java.math.BigDecimal.ZERO;

    // 公式建構器 (跟 margin-setting/計算公式管理同一套 FormulaEngine): 每一層都可選填一條算式,
    // 例如 "{NET_COST} * 1.15 + 2000"。留空 (null) 就沿用上面同一層的舊制 mode/value 算法當 fallback,
    // 兩邊不衝突, QuotationService.recalculateQuotationPricing() 逐層判斷。
    // 欄位本身沿用 migration_formula_pricing.sql 當時就建立好、但一直沒接上畫面/邏輯的 custom_*_formula
    // (只多補了 custom_basic_formula, 這一層是後來才拆出來的, 舊 migration 還沒有)。
    @Column(name = "custom_basic_formula", length = 500)
    private String customBasicFormula;

    @Column(name = "custom_trade_formula", length = 500)
    private String customTradeFormula;

    @Column(name = "custom_retail_formula", length = 500)
    private String customRetailFormula;

    @Column(name = "custom_rebate_formula", length = 500)
    private String customRebateFormula;

    @Column(name = "status")
    private String status = "draft"; // draft / locked / confirmed / expired

    @Column(name = "note")
    private String note;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "created_by")
    private int createdBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    public Quotation() {}

    public Quotation(int ITID, int AID, int version, Integer MSID, int groupSize, int createdBy) {
        this.ITID = ITID;
        this.AID = AID;
        this.version = version;
        this.MSID = MSID;
        this.groupSize = groupSize;
        this.createdBy = createdBy;
    }

    public int getQID() { return QID; }
    public void setQID(int QID) { this.QID = QID; }

    public int getITID() { return ITID; }
    public void setITID(int ITID) { this.ITID = ITID; }

    public int getAID() { return AID; }
    public void setAID(int AID) { this.AID = AID; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public Integer getMSID() { return MSID; }
    public void setMSID(Integer MSID) { this.MSID = MSID; }

    public String getFormulaMode() { return formulaMode; }
    public void setFormulaMode(String formulaMode) { this.formulaMode = formulaMode; }

    // 「已儲存的」是不是真的生效中 —— 除了 formula_mode 要是 "preset", 還要真的有選一組規則 (MSID != null),
    // 避免舊資料 (formula_mode 欄位 DB 預設值是 'preset', 但 MSID 一直是 null) 被誤判。畫面上的「已儲存的／自填」
    // 切換鈕、QuotationService 的計算邏輯都共用這個判斷, 只寫一次避免兩邊邏輯兜不起來。
    @Transient
    public boolean isPresetFormulaModeActive() {
        return "preset".equals(formulaMode) && MSID != null;
    }

    public int getGroupSize() { return groupSize; }
    public void setGroupSize(int groupSize) { this.groupSize = groupSize; }

    public String getGroupTierHeadcountMode() { return groupTierHeadcountMode; }
    public void setGroupTierHeadcountMode(String groupTierHeadcountMode) { this.groupTierHeadcountMode = groupTierHeadcountMode; }

    public String getBasicMarkupMode() { return basicMarkupMode; }
    public void setBasicMarkupMode(String basicMarkupMode) { this.basicMarkupMode = basicMarkupMode; }

    public java.math.BigDecimal getBasicMarkupValue() { return basicMarkupValue; }
    public void setBasicMarkupValue(java.math.BigDecimal basicMarkupValue) { this.basicMarkupValue = basicMarkupValue; }

    public String getTradeMarkupMode() { return tradeMarkupMode; }
    public void setTradeMarkupMode(String tradeMarkupMode) { this.tradeMarkupMode = tradeMarkupMode; }

    public java.math.BigDecimal getTradeMarkupValue() { return tradeMarkupValue; }
    public void setTradeMarkupValue(java.math.BigDecimal tradeMarkupValue) { this.tradeMarkupValue = tradeMarkupValue; }

    public String getRetailMarkupMode() { return retailMarkupMode; }
    public void setRetailMarkupMode(String retailMarkupMode) { this.retailMarkupMode = retailMarkupMode; }

    public java.math.BigDecimal getRetailMarkupValue() { return retailMarkupValue; }
    public void setRetailMarkupValue(java.math.BigDecimal retailMarkupValue) { this.retailMarkupValue = retailMarkupValue; }

    public String getRebateMode() { return rebateMode; }
    public void setRebateMode(String rebateMode) { this.rebateMode = rebateMode; }

    public java.math.BigDecimal getRebatePct() { return rebatePct; }
    public void setRebatePct(java.math.BigDecimal rebatePct) { this.rebatePct = rebatePct; }

    public String getCustomBasicFormula() { return customBasicFormula; }
    public void setCustomBasicFormula(String customBasicFormula) { this.customBasicFormula = customBasicFormula; }

    public String getCustomTradeFormula() { return customTradeFormula; }
    public void setCustomTradeFormula(String customTradeFormula) { this.customTradeFormula = customTradeFormula; }

    public String getCustomRetailFormula() { return customRetailFormula; }
    public void setCustomRetailFormula(String customRetailFormula) { this.customRetailFormula = customRetailFormula; }

    public String getCustomRebateFormula() { return customRebateFormula; }
    public void setCustomRebateFormula(String customRebateFormula) { this.customRebateFormula = customRebateFormula; }

    @Transient
    public boolean isBasicMarkupAmountMode() { return "AMOUNT".equals(basicMarkupMode); }

    @Transient
    public boolean isTradeMarkupAmountMode() { return "AMOUNT".equals(tradeMarkupMode); }

    @Transient
    public boolean isRetailMarkupAmountMode() { return "AMOUNT".equals(retailMarkupMode); }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public int getCreatedBy() { return createdBy; }
    public void setCreatedBy(int createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public LocalDateTime getLockedAt() { return lockedAt; }
    public void setLockedAt(LocalDateTime lockedAt) { this.lockedAt = lockedAt; }

    public LocalDateTime getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(LocalDateTime confirmedAt) { this.confirmedAt = confirmedAt; }

    // 是否已過期 (只看時間, 不寫回資料庫的狀態欄位, 由呼叫端決定要不要正式標記成 expired)
    @Transient
    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(LocalDateTime.now());
    }

    // 是否可編輯 (draft 才能改, 一旦上鎖/確認就凍結金額快照)
    @Transient
    public boolean isEditable() {
        return "draft".equals(status);
    }
}