package com.example.UsefulTravel.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

/**
 * 掛在單一報價項目 (QuotationLine) 底下的人數級距價錢, e.g.
 *   16~20人 → 39110.41
 *   21~26人 → 36637.69
 *   32人以上 → 32165.85 (max_qty 留空代表開放區間)
 * 每個項目各自可以有一套自己的級距切法 (遊覽車跟導遊費的級距通常不一樣)。
 */
@Entity
@Table(name = "quotation_line_tier")
public class QuotationLineTier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "QLTID")
    private int QLTID;

    @Column(name = "QLID")
    private int QLID;

    @Column(name = "min_qty")
    private int minQty;

    @Column(name = "max_qty")
    private Integer maxQty; // null = 開放區間 (這個級距以上都適用)

    @Column(name = "price")
    private BigDecimal price = BigDecimal.ZERO;

    @Column(name = "sort_order")
    private int sortOrder = 0;

    public QuotationLineTier() {}

    public QuotationLineTier(int QLID, int minQty, Integer maxQty, BigDecimal price, int sortOrder) {
        this.QLID = QLID;
        this.minQty = minQty;
        this.maxQty = maxQty;
        this.price = price;
        this.sortOrder = sortOrder;
    }

    public int getQLTID() { return QLTID; }
    public void setQLTID(int QLTID) { this.QLTID = QLTID; }

    public int getQLID() { return QLID; }
    public void setQLID(int QLID) { this.QLID = QLID; }

    public int getMinQty() { return minQty; }
    public void setMinQty(int minQty) { this.minQty = minQty; }

    public Integer getMaxQty() { return maxQty; }
    public void setMaxQty(Integer maxQty) { this.maxQty = maxQty; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    /** 這個人數是否落在這個級距內 (max 為 null 代表開放區間, 沒有上限)。 */
    @Transient
    public boolean matches(int groupSize) {
        if (groupSize < minQty) return false;
        return maxQty == null || groupSize <= maxQty;
    }
}
