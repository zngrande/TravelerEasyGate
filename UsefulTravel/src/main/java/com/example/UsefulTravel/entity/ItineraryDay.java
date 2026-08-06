package com.example.UsefulTravel.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

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
}
