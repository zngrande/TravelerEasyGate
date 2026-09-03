package com.example.travelereasygate.DAO;

import com.example.travelereasygate.entity.StaffUser;

import java.util.List;

public interface StaffUserDAO {
    void save(StaffUser staffUser);
    StaffUser findByAccount(String account);
    StaffUser findById(int UID);
    List<StaffUser> findByAgency(int AID);
}
