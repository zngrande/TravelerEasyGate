package com.example.travelereasygate.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

/** 級距範本裡的其中一列級距 (跟 QuotationLineTier 結構一樣, 只是屬於範本而不是屬於某一筆報價項目)。 */
@Entity
@Table(name = "price_tier_template_row")
public class PriceTierTemplateRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PTTRID")
    private int PTTRID;

    @Column(name = "PTTID")
    private int PTTID;

    @Column(name = "min_qty")
    private int minQty;

    @Column(name = "max_qty")
    private Integer maxQty;

    @Column(name = "price")
    private BigDecimal price = BigDecimal.ZERO;

    @Column(name = "sort_order")
    private int sortOrder = 0;

    public PriceTierTemplateRow() {}

    public PriceTierTemplateRow(int PTTID, int minQty, Integer maxQty, BigDecimal price, int sortOrder) {
        this.PTTID = PTTID;
        this.minQty = minQty;
        this.maxQty = maxQty;
        this.price = price;
        this.sortOrder = sortOrder;
    }

    public int getPTTRID() { return PTTRID; }
    public void setPTTRID(int PTTRID) { this.PTTRID = PTTRID; }

    public int getPTTID() { return PTTID; }
    public void setPTTID(int PTTID) { this.PTTID = PTTID; }

    public int getMinQty() { return minQty; }
    public void setMinQty(int minQty) { this.minQty = minQty; }

    public Integer getMaxQty() { return maxQty; }
    public void setMaxQty(Integer maxQty) { this.maxQty = maxQty; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
