package com.example.travelereasygate.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

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
 * 角色支援多選: 一個員工可以同時擁有多個角色 (例如 EDITOR+QUOTER, 但不用單獨開通 ADMIN 就能同時做行程
 * 編輯跟報價)。staff_user.role 存成逗號分隔的字串 (例如 "EDITOR,QUOTER"), 這個 service 判斷「這個角色
 * 組合能不能做某件事」一律用「這幾個角色裡面有沒有包含 X」的方式檢查, 不是單一值的 equals 比對。
 * 只要角色組合裡「任何一個」角色允許做這件事, 整組就允許 (取聯集, 不是交集)——例如 EDITOR+VIEWER 的人
 * 一樣可以編輯行程, 不會因為同時掛了 VIEWER 就被反過來限制住。
 * 這個 service 只做「這個角色能不能做某件事」的判斷, 不碰 session/資料庫, 方便各 controller 共用、也方便單元測試。
 */
@Service
public class PermissionService {

    public static final String ADMIN = "ADMIN";
    public static final String EDITOR = "EDITOR";
    public static final String QUOTER = "QUOTER";
    public static final String VIEWER = "VIEWER";

    /** 把逗號分隔的角色字串拆成清單, 每個項目都去除前後空白、忽略空字串。null/空字串回傳空清單。 */
    public List<String> splitRoles(String role) {
        List<String> result = new ArrayList<>();
        if (role == null || role.isBlank()) return result;
        for (String token : role.split(",")) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) result.add(trimmed);
        }
        return result;
    }

    /** 把多選的角色清單組回存進 staff_user.role 用的逗號分隔字串, 沒選任何角色時預設為 VIEWER (最低權限)。 */
    public String joinRoles(List<String> roles) {
        List<String> valid = new ArrayList<>();
        if (roles != null) {
            for (String r : roles) {
                if (isValidRole(r) && !valid.contains(r)) valid.add(r);
            }
        }
        if (valid.isEmpty()) return VIEWER;
        return String.join(",", valid);
    }

    private boolean hasRole(String role, String target) {
        return splitRoles(role).contains(target);
    }

    /** 可以編輯行程內容 (排版看板上的天數/景點/拉車等)。 */
    public boolean canEditItinerary(String role) {
        return hasRole(role, ADMIN) || hasRole(role, EDITOR);
    }

    /** 可以製作/調整報價單。 */
    public boolean canQuote(String role) {
        return hasRole(role, ADMIN) || hasRole(role, QUOTER);
    }

    /** 可以在共用槽上架/調整報價 (需求文件第四章, 共用槽機制)。 */
    public boolean canShareQuote(String role) {
        return hasRole(role, ADMIN) || hasRole(role, QUOTER);
    }

    /** 閱覽：四種角色都可以, 只要是登入的員工帳號。 */
    public boolean canView(String role) {
        return role != null && !role.isBlank();
    }

    /** 帳號管理 (新增員工/停用員工/改角色) 只有 ADMIN 能做。 */
    public boolean canManageStaff(String role) {
        return hasRole(role, ADMIN);
    }

    /** role (單一角色代碼, 不是逗號分隔字串) 是否為這四種合法值之一, 給表單驗證/checkbox 選項用。 */
    public boolean isValidRole(String role) {
        return ADMIN.equals(role) || EDITOR.equals(role) || QUOTER.equals(role) || VIEWER.equals(role);
    }

    /** role (可能是逗號分隔的多選字串) 裡每一段是否都是合法角色代碼、而且至少有一個——給存檔前的整體驗證用。 */
    public boolean isValidRoleString(String role) {
        List<String> tokens = splitRoles(role);
        if (tokens.isEmpty()) return false;
        for (String token : tokens) {
            if (!isValidRole(token)) return false;
        }
        return true;
    }
}
