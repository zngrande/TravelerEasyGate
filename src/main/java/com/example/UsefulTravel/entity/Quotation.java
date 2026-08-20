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

    @Column(name = "group_size")
    private int groupSize = 1;

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

    public int getGroupSize() { return groupSize; }
    public void setGroupSize(int groupSize) { this.groupSize = groupSize; }

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
