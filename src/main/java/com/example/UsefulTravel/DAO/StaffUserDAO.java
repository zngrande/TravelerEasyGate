package com.example.UsefulTravel.DAO;

import com.example.UsefulTravel.entity.StaffUser;

import java.util.List;

public interface StaffUserDAO {
    void save(StaffUser staffUser);
    StaffUser findByAccount(String account);
    StaffUser findById(int UID);
    List<StaffUser> findByAgency(int AID);
}
