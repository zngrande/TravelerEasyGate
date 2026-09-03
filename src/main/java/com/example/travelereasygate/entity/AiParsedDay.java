package com.example.travelereasygate.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "ai_parsed_day")
public class AiParsedDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "APDID")
    private int APDID;

    @Column(name = "IPID")
    private int IPID;

    @Column(name = "day_number")
    private int dayNumber;

    @Column(name = "theme")
    private String theme;

    public AiParsedDay() {}

    public AiParsedDay(int IPID, int dayNumber, String theme) {
        this.IPID = IPID;
        this.dayNumber = dayNumber;
        this.theme = theme;
    }

    public int getAPDID() { return APDID; }
    public void setAPDID(int APDID) { this.APDID = APDID; }

    public int getIPID() { return IPID; }
    public void setIPID(int IPID) { this.IPID = IPID; }

    public int getDayNumber() { return dayNumber; }
    public void setDayNumber(int dayNumber) { this.dayNumber = dayNumber; }

    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = theme; }
}
