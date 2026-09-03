package com.example.travelereasygate.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

/**
 * 取代 Spring Boot 預設的 Whitelabel Error Page (那個純白底、印一堆英文/Java stack trace 的錯誤畫面)。
 *
 * 原本這裡是實作 org.springframework.boot.web.servlet.error.ErrorController, 但那個套件在
 * Spring Boot 4 被移掉/搬家了 (編譯直接報 "package ... does not exist"), 而且 Spring Boot 內部
 * 錯誤處理相關的套件路徑本來就比較容易隨版本改動。改用這裡的寫法: 只依賴
 * org.springframework.web.bind.annotation.* 這幾個從 Spring 3.x 就有、非常穩定、幾乎不會再變動的
 * 核心 annotation, 不吃 Spring Boot 內部實作細節, 換 Spring Boot 版本也不容易再中招。
 *
 * 運作方式: 任何 @Controller 方法丟出例外, 都會被這裡攔截 (取代 Spring MVC 預設走到 /error
 * 那條路的行為), 畫面上只顯示「系統發生錯誤」+ 一組簡短的參考代碼, 完整例外內容 (含 stack trace)
 * 改寫進伺服器 log, 用同一組參考代碼當關鍵字, 之後要查真正的錯誤原因, 直接在 log 裡搜這組代碼就找得到。
 *
 * 覆蓋範圍: 這個機制攔的到「Controller 方法執行過程中丟出的例外」(目前系統遇到的 SQL/Hibernate 例外
 * 幾乎都屬於這種), 但攔不到「找不到對應網址」(404, 根本沒有 Controller 方法被呼叫到) 這種情況——
 * 404 改用 Spring Boot 內建的慣例處理: 只要 templates/error/404.html 這個檔案存在, 不用寫任何
 * Java 程式碼, Spring Boot 會自動選用它顯示 (見 error/404.html、error/5xx.html 這兩個檔案),
 * 這條路徑完全不依賴前面提到那個容易變動的內部套件, 換版本也不會壞。
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleException(Exception ex, HttpServletRequest request, Model model) {
        // 8 碼英數參考代碼, 短好唸好打字給客服用, 不是拿來當唯一索引用的正式 ID, 只是方便人工對照 log
        String errorId = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        log.error("[錯誤參考代碼 {}] uri={}", errorId, request.getRequestURI(), ex);

        model.addAttribute("errorId", errorId);
        return "error/500";
    }
}
