package com.example.UsefulTravel.controller;

import com.example.UsefulTravel.DAO.StaffUserDAO;
import com.example.UsefulTravel.entity.StaffUser;
import com.example.UsefulTravel.service.AuthService;
import com.example.UsefulTravel.service.PermissionService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * 帳號體系管理 (需求文件 1.1):
 *   - /account  : 登入者自己改暱稱/電話/密碼
 *   - /staff    : 管理者 (ADMIN) 管理公司底下所有員工帳號 — 新增、改角色、停用/啟用、重設密碼
 */
@Controller
public class StaffUserController {

    private final AuthService authService;
    private final StaffUserDAO staffUserDAO;
    private final PermissionService permissionService;

    @Autowired
    public StaffUserController(AuthService authService, StaffUserDAO staffUserDAO, PermissionService permissionService) {
        this.authService = authService;
        this.staffUserDAO = staffUserDAO;
        this.permissionService = permissionService;
    }

    // ------------------------------------------------------------
    // 個人帳號設定 (任何登入者都能用)
    // ------------------------------------------------------------

    @GetMapping("/account")
    public String accountPage(HttpSession session, Model model) {
        Integer UID = (Integer) session.getAttribute("UID");
        if (UID == null) return "redirect:/login";

        model.addAttribute("staff", staffUserDAO.findById(UID));
        return "account/settings";
    }

    // POST /account/profile → 使用者名稱設置 (顯示名稱/電話)
    @PostMapping("/account/profile")
    public String updateProfile(@RequestParam String name,
                                 @RequestParam(required = false) String phone,
                                 HttpSession session,
                                 RedirectAttributes redirectAttributes) {
        Integer UID = (Integer) session.getAttribute("UID");
        if (UID == null) return "redirect:/login";

        authService.updateProfile(UID, name, phone);
        session.setAttribute("name", name); // 側邊欄馬上顯示新名字, 不用重新登入
        redirectAttributes.addFlashAttribute("accountMessage", "個人資料已更新");
        return "redirect:/account";
    }

    // POST /account/password → 使用者自助改密碼
    @PostMapping("/account/password")
    public String changePassword(@RequestParam String oldPw,
                                  @RequestParam String newPw,
                                  HttpSession session,
                                  RedirectAttributes redirectAttributes) {
        Integer UID = (Integer) session.getAttribute("UID");
        if (UID == null) return "redirect:/login";

        boolean ok = authService.changePassword(UID, oldPw, newPw);
        redirectAttributes.addFlashAttribute("accountMessage", ok ? "密碼已更新" : "舊密碼不正確，密碼未變更");
        return "redirect:/account";
    }

    // ------------------------------------------------------------
    // 員工帳號管理 (僅 ADMIN)
    // ------------------------------------------------------------

    @GetMapping("/staff")
    public String staffList(HttpSession session, Model model) {
        Integer AID = (Integer) session.getAttribute("AID");
        String role = (String) session.getAttribute("role");
        if (AID == null) return "redirect:/login";
        if (!permissionService.canManageStaff(role)) return "redirect:/agency/dashboard";

        List<StaffUser> staffList = staffUserDAO.findByAgency(AID);
        model.addAttribute("staffList", staffList);
        model.addAttribute("currentUID", session.getAttribute("UID"));
        return "account/staff-list";
    }

    // POST /staff/new → 新增員工帳號
    @PostMapping("/staff/new")
    public String addStaff(@RequestParam String name,
                            @RequestParam(required = false) String phone,
                            @RequestParam String account,
                            @RequestParam String pw,
                            @RequestParam String role,
                            HttpSession session,
                            RedirectAttributes redirectAttributes) {
        Integer AID = (Integer) session.getAttribute("AID");
        String sessionRole = (String) session.getAttribute("role");
        if (AID == null) return "redirect:/login";
        if (!permissionService.canManageStaff(sessionRole)) return "redirect:/agency/dashboard";
        if (!permissionService.isValidRole(role)) role = PermissionService.VIEWER;

        if (staffUserDAO.findByAccount(account) != null) {
            redirectAttributes.addFlashAttribute("staffError", "這個帳號已經被使用過了");
            return "redirect:/staff";
        }

        authService.addStaff(AID, name, phone, account, pw, role);
        return "redirect:/staff";
    }

    // POST /staff/{uid}/role → 變更角色 (四級權限矩陣)
    @PostMapping("/staff/{uid}/role")
    public String updateRole(@PathVariable("uid") int UID, @RequestParam String role,
                              HttpSession session, RedirectAttributes redirectAttributes) {
        Integer AID = (Integer) session.getAttribute("AID");
        String sessionRole = (String) session.getAttribute("role");
        if (AID == null) return "redirect:/login";
        if (!permissionService.canManageStaff(sessionRole)) return "redirect:/agency/dashboard";
        if (!permissionService.isValidRole(role)) {
            redirectAttributes.addFlashAttribute("staffError", "無效的角色");
            return "redirect:/staff";
        }

        StaffUser target = staffUserDAO.findById(UID);
        if (target == null || target.getAID() != AID) return "redirect:/staff";

        authService.updateRole(UID, role);
        return "redirect:/staff";
    }

    // POST /staff/{uid}/toggle-active → 停用/啟用員工帳號 (「註銷使用者」)
    @PostMapping("/staff/{uid}/toggle-active")
    public String toggleActive(@PathVariable("uid") int UID, HttpSession session, RedirectAttributes redirectAttributes) {
        Integer AID = (Integer) session.getAttribute("AID");
        Integer sessionUID = (Integer) session.getAttribute("UID");
        String sessionRole = (String) session.getAttribute("role");
        if (AID == null) return "redirect:/login";
        if (!permissionService.canManageStaff(sessionRole)) return "redirect:/agency/dashboard";

        StaffUser target = staffUserDAO.findById(UID);
        if (target == null || target.getAID() != AID) return "redirect:/staff";
        if (sessionUID != null && sessionUID == UID) {
            redirectAttributes.addFlashAttribute("staffError", "不能停用自己目前登入中的帳號");
            return "redirect:/staff";
        }

        authService.setActive(UID, !target.isActive());
        return "redirect:/staff";
    }

    // POST /staff/{uid}/reset-password → 管理者手動重設員工密碼 (自助忘記密碼流程之前的過渡方案)
    @PostMapping("/staff/{uid}/reset-password")
    public String resetPassword(@PathVariable("uid") int UID, @RequestParam String newPw,
                                 HttpSession session, RedirectAttributes redirectAttributes) {
        Integer AID = (Integer) session.getAttribute("AID");
        String sessionRole = (String) session.getAttribute("role");
        if (AID == null) return "redirect:/login";
        if (!permissionService.canManageStaff(sessionRole)) return "redirect:/agency/dashboard";

        StaffUser target = staffUserDAO.findById(UID);
        if (target == null || target.getAID() != AID) return "redirect:/staff";

        authService.resetPasswordByAdmin(UID, newPw);
        redirectAttributes.addFlashAttribute("staffError", "已重設「" + target.getName() + "」的密碼");
        return "redirect:/staff";
    }
}
