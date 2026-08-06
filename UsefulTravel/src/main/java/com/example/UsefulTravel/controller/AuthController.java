package com.example.UsefulTravel.controller;

import com.example.UsefulTravel.entity.StaffUser;
import com.example.UsefulTravel.service.AuthService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
public class AuthController {

    private final AuthService authService;

    @Autowired
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // GET /login → 登入頁面
    @GetMapping("/login")
    public String loginPage() {
        return "auth/login";
    }

    // POST /login → 驗證帳號密碼
    @PostMapping("/login")
    public String login(@RequestParam String account,
                         @RequestParam String pw,
                         HttpSession session) {
        StaffUser staff = authService.login(account, pw);
        if (staff == null) {
            return "redirect:/login?error=true";
        }
        session.setAttribute("UID", staff.getUID());
        session.setAttribute("AID", staff.getAID());
        session.setAttribute("name", staff.getName());
        session.setAttribute("role", staff.getRole());
        return "redirect:/agency/dashboard";
    }

    // GET /logout
    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }

    // GET /register → 旅行社註冊頁 (建立 agency + 第一個管理員帳號)
    @GetMapping("/register")
    public String registerPage() {
        return "auth/register";
    }

    // POST /register
    @PostMapping("/register")
    public String register(@RequestParam String agencyName,
                            @RequestParam(required = false) String licenseNo,
                            @RequestParam String contactPhone,
                            @RequestParam String adminName,
                            @RequestParam String account,
                            @RequestParam String pw) {
        authService.registerAgency(agencyName, licenseNo, contactPhone, adminName, account, pw);
        return "redirect:/login";
    }
}
