package com.example.UsefulTravel.entity;

import jakarta.persistence.*;

/** 公式範本裡的其中一層 (basic / trade / retail / rebate)，一個範本最多四列。 */
@Entity
@Table(name = "formula_template_line")
public class FormulaTemplateLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "FTLID")
    private int FTLID;

    @Column(name = "FTID")
    private int FTID;

    // basic / trade / retail / rebate，對應 quotation 的 xxx_markup_formula 欄位
    @Column(name = "layer_key")
    private String layerKey;

    @Column(name = "formula_expr")
    private String formulaExpr;

    public FormulaTemplateLine() {}

    public FormulaTemplateLine(int FTID, String layerKey, String formulaExpr) {
        this.FTID = FTID;
        this.layerKey = layerKey;
        this.formulaExpr = formulaExpr;
    }

    public int getFTLID() { return FTLID; }
    public void setFTLID(int FTLID) { this.FTLID = FTLID; }

    public int getFTID() { return FTID; }
    public void setFTID(int FTID) { this.FTID = FTID; }

    public String getLayerKey() { return layerKey; }
    public void setLayerKey(String layerKey) { this.layerKey = layerKey; }

    public String getFormulaExpr() { return formulaExpr; }
    public void setFormulaExpr(String formulaExpr) { this.formulaExpr = formulaExpr; }
}
