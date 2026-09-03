package com.example.travelereasygate.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "staff_user")
public class StaffUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "UID")
    private int UID;

    @Column(name = "AID")
    private int AID;

    @Column(name = "name")
    private String name;

    @Column(name = "phone")
    private String phone;

    @Column(name = "account")
    private String account;

    @Column(name = "pw")
    private String pw;

    @Column(name = "role")
    private String role = "OP"; // ADMIN / EDITOR / QUOTER / VIEWER (四級權限矩陣, 見需求文件 1.2)

    @Column(name = "is_active")
    private boolean isActive = true; // 停用 (非刪除, 避免歷史紀錄斷鏈)

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public StaffUser() {}

    public StaffUser(int AID, String name, String phone, String account, String pw, String role) {
        this.AID = AID;
        this.name = name;
        this.phone = phone;
        this.account = account;
        this.pw = pw;
        this.role = role;
    }

    public int getUID() { return UID; }
    public void setUID(int UID) { this.UID = UID; }

    public int getAID() { return AID; }
    public void setAID(int AID) { this.AID = AID; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getAccount() { return account; }
    public void setAccount(String account) { this.account = account; }

    public String getPw() { return pw; }
    public void setPw(String pw) { this.pw = pw; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "StaffUser{UID=" + UID + ", name='" + name + "', role='" + role + "'}";
    }
}
