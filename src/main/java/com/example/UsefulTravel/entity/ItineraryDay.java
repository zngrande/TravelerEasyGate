package com.example.UsefulTravel.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "itinerary_day")
public class ItineraryDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "IDID")
    private int IDID;

    @Column(name = "ITID")
    private int ITID;

    @Column(name = "day_number")
    private int dayNumber;

    @Column(name = "day_date")
    private LocalDate dayDate;

    @Column(name = "theme")
    private String theme;

    // Patch 27: 這一天指定要安排的城市 (可能不只一個, 用「、」分隔, 跟 country/region 同一套格式) ——
    // 取代原本整趟行程共用一段自由文字「行程說明」的做法, 改成逐天指定, 「AI 安排行程」排這一天時只會
    // 從這個城市底下的景點/餐廳/飯店挑選。NULL/空字串代表這天沒有指定城市 (通常是班機/轉機日),
    // AI 安排行程時會完全跳過這天, 不強制排任何景點/餐食/住宿。
    @Column(name = "planned_cities")
    private String plannedCities;

    @Column(name = "start_time")
    private LocalTime startTime = LocalTime.of(9, 0); // 這天的出發時間, 預設早上9點, 用來算時間軸

    @Column(name = "transport_mode")
    private String transportMode = "driving"; // driving / walking, 決定拉車時間怎麼算

    public ItineraryDay() {}

    public ItineraryDay(int ITID, int dayNumber, LocalDate dayDate, String theme) {
        this.ITID = ITID;
        this.dayNumber = dayNumber;
        this.dayDate = dayDate;
        this.theme = theme;
    }

    public int getIDID() { return IDID; }
    public void setIDID(int IDID) { this.IDID = IDID; }

    public int getITID() { return ITID; }
    public void setITID(int ITID) { this.ITID = ITID; }

    public int getDayNumber() { return dayNumber; }
    public void setDayNumber(int dayNumber) { this.dayNumber = dayNumber; }

    public LocalDate getDayDate() { return dayDate; }
    public void setDayDate(LocalDate dayDate) { this.dayDate = dayDate; }

    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = theme; }

    public String getPlannedCities() { return plannedCities; }
    public void setPlannedCities(String plannedCities) { this.plannedCities = plannedCities; }

    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }

    public String getTransportMode() { return transportMode; }
    public void setTransportMode(String transportMode) { this.transportMode = transportMode; }
}
