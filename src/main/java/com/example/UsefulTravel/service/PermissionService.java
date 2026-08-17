package com.example.UsefulTravel.service;

import org.springframework.stereotype.Service;

/**
 * 四級權限矩陣 (需求文件 1.2)：
 *
 *  角色                | 編輯行程 | 製作/調整報價 | 共用槽報價 | 閱覽
 *  ------------------- | -------- | -------------- | ---------- | ----
 *  ADMIN  (全權限/主管)  |   可     |      可        |     可     |  可
 *  EDITOR (行程編輯者)   |   可     |     不可       |    不可    |  可
 *  QUOTER (報價專員)     |  不可    |      可        |     可     |  可
 *  VIEWER (唯讀)        |  不可    |     不可       |    不可    |  可
 *
 * 角色字串存在 staff_user.role, 值即為上表左欄 (ADMIN/EDITOR/QUOTER/VIEWER)。
 * 這個 service 只做「這個角色能不能做某件事」的判斷, 不碰 session/資料庫, 方便各 controller 共用、也方便單元測試。
 */
@Service
public class PermissionService {

    public static final String ADMIN = "ADMIN";
    public static final String EDITOR = "EDITOR";
    public static final String QUOTER = "QUOTER";
    public static final String VIEWER = "VIEWER";

    /** 可以編輯行程內容 (排版看板上的天數/景點/拉車等)。 */
    public boolean canEditItinerary(String role) {
        return ADMIN.equals(role) || EDITOR.equals(role);
    }

    /** 可以製作/調整報價單。 */
    public boolean canQuote(String role) {
        return ADMIN.equals(role) || QUOTER.equals(role);
    }

    /** 可以在共用槽上架/調整報價 (需求文件第四章, 共用槽機制)。 */
    public boolean canShareQuote(String role) {
        return ADMIN.equals(role) || QUOTER.equals(role);
    }

    /** 閱覽：四種角色都可以, 只要是登入的員工帳號。 */
    public boolean canView(String role) {
        return role != null;
    }

    /** 帳號管理 (新增員工/停用員工/改角色) 只有 ADMIN 能做。 */
    public boolean canManageStaff(String role) {
        return ADMIN.equals(role);
    }

    /** role 是否為這四種合法值之一, 給表單驗證/下拉選單用。 */
    public boolean isValidRole(String role) {
        return ADMIN.equals(role) || EDITOR.equals(role) || QUOTER.equals(role) || VIEWER.equals(role);
    }
}
