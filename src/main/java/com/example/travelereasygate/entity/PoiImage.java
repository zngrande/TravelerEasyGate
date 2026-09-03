package com.example.travelereasygate.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "poi_image")
public class PoiImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "IID")
    private int IID;

    @Column(name = "PID")
    private int PID;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "sort_order")
    private int sortOrder;

    public PoiImage() {}

    public PoiImage(int PID, String imageUrl, int sortOrder) {
        this.PID = PID;
        this.imageUrl = imageUrl;
        this.sortOrder = sortOrder;
    }

    public int getIID() { return IID; }
    public void setIID(int IID) { this.IID = IID; }

    public int getPID() { return PID; }
    public void setPID(int PID) { this.PID = PID; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
