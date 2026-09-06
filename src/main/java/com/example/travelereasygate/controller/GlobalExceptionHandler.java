package com.example.travelereasygate.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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
 * 覆蓋範圍: 這個機制原本設計是想攔「Controller 方法執行過程中丟出的例外」(目前系統遇到的 SQL/Hibernate
 * 例外幾乎都屬於這種), 「找不到對應網址」(404, 根本沒有 Controller 方法被呼叫到) 這種情況原本預期會直接
 * 交給 Spring Boot 內建的慣例處理 (只要 templates/error/404.html 這個檔案存在就會自動選用它顯示,
 * 見 error/404.html、error/5xx.html 這兩個檔案)。
 *
 * 但使用者回報的伺服器 log 顯示事實不是這樣: 瀏覽器自動請求 /favicon.ico、Chrome 自動探測的
 * /.well-known/appspecific/com.chrome.devtools.json 這種「根本不存在的靜態資源」, 拋出的
 * NoResourceFoundException 其實還是會被下面這個 @ExceptionHandler(Exception.class) 攔截到
 * (NoResourceFoundException 也是 RuntimeException 的子類別, DispatcherServlet 處理
 * ResourceHttpRequestHandler 丟出的例外時一樣會經過 @ControllerAdvice 這層, 不是原本以為的「完全繞過」)——
 * 結果就是每次瀏覽器背景請求一次 favicon.ico, log 裡就會多一筆 ERROR 等級、附完整 stack trace 的紀錄,
 * 畫面上也會顯示成看起來像系統壞掉的「系統發生錯誤」500 頁面, 而不是單純的 404——這其實只是雜訊,
 * 卻很容易被誤認成真正的錯誤 (使用者這次回報問題時就把這幾筆 favicon.ico/.well-known 的 log 一起貼過來,
 * 以為是同一個 bug 的一部分)。
 *
 * 修正: 另外補一個專門攔 NoResourceFoundException 的 @ExceptionHandler (Spring 對同一個
 * @ControllerAdvice 裡的多個 @ExceptionHandler, 會優先選最貼近例外實際型別的那個, 所以這個新方法會
 * 優先於下面那個 Exception.class 的 catch-all 生效, 不用調整順序或排除清單), 讓它照 404 該有的樣子處理:
 * 回應狀態碼維持 404、顯示原本就有的 error/404.html、log 只寫 DEBUG 等級 (不是 ERROR, 也不印完整
 * stack trace)——這種請求本來就是瀏覽器自動背景行為, 不是應用程式真的壞掉, 不需要用 ERROR 等級去
 * 驚動查 log 的人。
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNoResourceFound(NoResourceFoundException ex, HttpServletRequest request) {
        log.debug("找不到靜態資源 (瀏覽器背景請求, 非應用程式錯誤): {}", request.getRequestURI());
        return "error/404";
    }

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