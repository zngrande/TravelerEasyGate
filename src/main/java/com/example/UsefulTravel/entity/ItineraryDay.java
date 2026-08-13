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

    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }

    public String getTransportMode() { return transportMode; }
    public void setTransportMode(String transportMode) { this.transportMode = transportMode; }
}
