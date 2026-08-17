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
     * 邀請 / 新增員工帳號
     */
    public void addStaff(int AID, String name, String phone, String account, String rawPw, String role) {
        StaffUser staff = new StaffUser(AID, name, phone, account, encoder.encode(rawPw), role);
        staffUserDAO.save(staff);
    }

    public StaffUser login(String account, String rawPw) {
        StaffUser staff = staffUserDAO.findByAccount(account);
        if (staff == null) return null;
        if (!staff.isActive()) return null; // 已停用的帳號不給登入 (註銷使用者, 需求文件 1.1)
        return encoder.matches(rawPw, staff.getPw()) ? staff : null;
    }

    /**
     * 使用者自助改密碼: 要先驗證舊密碼才能改, 避免帳號在螢幕沒鎖的情況下被別人亂改密碼鎖死本人。
     * 回傳 false 代表舊密碼不對, 沒有改成功。
     */
    public boolean changePassword(int UID, String oldPw, String newPw) {
        StaffUser staff = staffUserDAO.findById(UID);
        if (staff == null || !encoder.matches(oldPw, staff.getPw())) return false;
        staff.setPw(encoder.encode(newPw));
        staffUserDAO.save(staff);
        return true;
    }

    /** 使用者名稱設置 (需求文件 1.1「使用者名稱設置」): 改顯示名稱/電話, 帳號 (登入用) 不開放自己改, 避免跟登入紀錄對不上。 */
    public void updateProfile(int UID, String name, String phone) {
        StaffUser staff = staffUserDAO.findById(UID);
        if (staff == null) return;
        staff.setName(name);
        staff.setPhone(phone);
        staffUserDAO.save(staff);
    }

    /** 管理者變更員工角色 (四級權限矩陣: ADMIN/EDITOR/QUOTER/VIEWER)。 */
    public void updateRole(int UID, String role) {
        StaffUser staff = staffUserDAO.findById(UID);
        if (staff == null) return;
        staff.setRole(role);
        staffUserDAO.save(staff);
    }

    /**
     * 停用/啟用員工帳號 (「註銷使用者」= 停用, 不是刪除, 避免歷史紀錄斷鏈, 需求文件 1.1)。
     * 管理者專用的重設密碼 (忘記密碼的自助流程還沒做前, 先讓 ADMIN 能手動重設)。
     */
    public void setActive(int UID, boolean active) {
        StaffUser staff = staffUserDAO.findById(UID);
        if (staff == null) return;
        staff.setActive(active);
        staffUserDAO.save(staff);
    }

    public void resetPasswordByAdmin(int UID, String newPw) {
        StaffUser staff = staffUserDAO.findById(UID);
        if (staff == null) return;
        staff.setPw(encoder.encode(newPw));
        staffUserDAO.save(staff);
    }
}
