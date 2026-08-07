package com.example.UsefulTravel.service;

import com.example.UsefulTravel.DAO.AgencyDAO;
import com.example.UsefulTravel.DAO.StaffUserDAO;
import com.example.UsefulTravel.entity.Agency;
import com.example.UsefulTravel.entity.StaffUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * AuthService - 處理旅行社註冊、員工登入
 * TODO: 目前密碼用 BCrypt 雜湊, 正式上線前記得檢查 pom.xml 是否已含 spring-security-crypto
 */
@Service
public class AuthService {

    private final AgencyDAO agencyDAO;
    private final StaffUserDAO staffUserDAO;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Autowired
    public AuthService(AgencyDAO agencyDAO, StaffUserDAO staffUserDAO) {
        this.agencyDAO = agencyDAO;
        this.staffUserDAO = staffUserDAO;
    }

    /**
     * 旅行社首次註冊: 同時建立 agency + 第一位管理員帳號
     */
    public void registerAgency(String agencyName, String licenseNo, String contactPhone,
                                String adminName, String account, String rawPw) {
        Agency agency = new Agency(agencyName, licenseNo, contactPhone, account);
        agencyDAO.save(agency);

        StaffUser admin = new StaffUser(
                agency.getAID(), adminName, contactPhone, account,
                encoder.encode(rawPw), "ADMIN"
        );
        staffUserDAO.save(admin);
    }

    /**
     * 邀請 / 新增員工帳號 (OP)
     */
    public void addStaff(int AID, String name, String phone, String account, String rawPw, String role) {
        StaffUser staff = new StaffUser(AID, name, phone, account, encoder.encode(rawPw), role);
        staffUserDAO.save(staff);
    }

    public StaffUser login(String account, String rawPw) {
        StaffUser staff = staffUserDAO.findByAccount(account);
        if (staff == null) return null;
        return encoder.matches(rawPw, staff.getPw()) ? staff : null;
    }
}
