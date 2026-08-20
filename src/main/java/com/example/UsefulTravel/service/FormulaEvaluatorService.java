package com.example.UsefulTravel.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 公式定價的運算引擎 (報價 Formula Builder 用)。
 *
 * 只支援 + - * / × ÷ ( ) 四則運算、純數字、以及 {變數id} 這種變數引用，
 * 刻意自己寫遞迴下降解析器，不用 ScriptEngine/eval —— 使用者輸入的公式不應該有機會
 * 變成可執行程式碼，這是安全上的硬性要求，不是效能考量。
 *
 * 前端 (quotation/edit.html 裡的公式編輯區) 會把使用者組好的公式序列化成同一種字串格式，
 * 例如: {nnet}×1.15+2000，儲存到 quotation.basic_markup_formula 等欄位，
 * 或存進 formula_template_line.formula_expr。
 */
@Service
public class FormulaEvaluatorService {

    private static final int SCALE = 6;

    // 變數引用 {xxx} / 數字 (含小數) / 四則運算子 (半形*/ 跟中文×÷都接受，因為前端色塊插入的是中文符號，
    // 使用者手動輸入時比較習慣打半形) / 括號。空白會在 tokenize 前先剔除。
    private static final Pattern TOKEN_PATTERN =
            Pattern.compile("\\{[^{}]+\\}|\\d+(\\.\\d+)?|[+\\-*/×÷()]");

    /** 帶入實際數值算出結果。formula 為 null/空白會直接丟例外，呼叫端要自行決定失敗時的退回策略。 */
    public BigDecimal evaluate(String formula, Map<String, BigDecimal> variables) {
        if (formula == null || formula.isBlank()) {
            throw new IllegalArgumentException("公式是空的");
        }
        List<String> tokens = tokenize(formula);
        Parser parser = new Parser(tokens, variables == null ? Map.of() : variables);
        BigDecimal result = parser.parseExpression();
        if (!parser.isAtEnd()) {
            throw new IllegalArgumentException("公式格式錯誤 (結尾有多餘的內容): " + formula);
        }
        return result.setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 存檔前驗證用: 檢查公式語法合不合法、有沒有引用不在允許清單內的變數。
     * 只驗證語法，不代表真實資料代入後一定不會除以 0 (那個留到 evaluate() 實際計算時再擋)。
     */
    public void validate(String formula, Set<String> allowedVarIds) {
        if (formula == null || formula.isBlank()) {
            throw new IllegalArgumentException("公式是空的");
        }
        List<String> tokens = tokenize(formula);
        for (String t : tokens) {
            if (t.startsWith("{")) {
                String id = t.substring(1, t.length() - 1);
                if (allowedVarIds == null || !allowedVarIds.contains(id)) {
                    throw new IllegalArgumentException("公式引用了不存在或這一層不能用的變數: " + id);
                }
            }
        }
        Map<String, BigDecimal> dummy = new HashMap<>();
        if (allowedVarIds != null) {
            for (String id : allowedVarIds) dummy.put(id, BigDecimal.ONE);
        }
        evaluate(formula, dummy); // 全部帶 1 跑一次語法, 順便抓出結構性錯誤 (括號沒對齊等)
    }

    private List<String> tokenize(String formula) {
        String compact = formula.replaceAll("\\s+", "");
        if (compact.isEmpty()) {
            throw new IllegalArgumentException("公式是空的");
        }
        Matcher m = TOKEN_PATTERN.matcher(compact);
        List<String> tokens = new ArrayList<>();
        int lastEnd = 0;
        while (m.find()) {
            if (m.start() != lastEnd) {
                throw new IllegalArgumentException(
                        "公式含有無法辨識的內容: " + compact.substring(lastEnd, m.start()));
            }
            tokens.add(m.group());
            lastEnd = m.end();
        }
        if (lastEnd != compact.length()) {
            throw new IllegalArgumentException("公式含有無法辨識的內容: " + compact.substring(lastEnd));
        }
        return tokens;
    }

    /** 遞迴下降解析器: expression = term (('+'|'-') term)*；term = factor (('*'|'/') factor)* */
    private static final class Parser {
        private final List<String> tokens;
        private final Map<String, BigDecimal> variables;
        private int pos = 0;

        Parser(List<String> tokens, Map<String, BigDecimal> variables) {
            this.tokens = tokens;
            this.variables = variables;
        }

        boolean isAtEnd() { return pos >= tokens.size(); }
        private String peek() { return isAtEnd() ? null : tokens.get(pos); }
        private String next() { return tokens.get(pos++); }

        BigDecimal parseExpression() {
            BigDecimal value = parseTerm();
            while ("+".equals(peek()) || "-".equals(peek())) {
                String op = next();
                BigDecimal rhs = parseTerm();
                value = "+".equals(op) ? value.add(rhs) : value.subtract(rhs);
            }
            return value;
        }

        private BigDecimal parseTerm() {
            BigDecimal value = parseFactor();
            while (isMul(peek()) || isDiv(peek())) {
                String op = next();
                BigDecimal rhs = parseFactor();
                if (isMul(op)) {
                    value = value.multiply(rhs);
                } else {
                    if (rhs.compareTo(BigDecimal.ZERO) == 0) {
                        throw new IllegalArgumentException("公式除以 0");
                    }
                    value = value.divide(rhs, SCALE, RoundingMode.HALF_UP);
                }
            }
            return value;
        }

        private BigDecimal parseFactor() {
            String t = peek();
            if (t == null) throw new IllegalArgumentException("公式不完整");
            if ("(".equals(t)) {
                next();
                BigDecimal value = parseExpression();
                if (!")".equals(peek())) throw new IllegalArgumentException("括號沒有對應的結尾");
                next();
                return value;
            }
            if ("-".equals(t)) { // 允許負號開頭, 例如 -{misc}
                next();
                return parseFactor().negate();
            }
            t = next();
            if (t.startsWith("{")) {
                String id = t.substring(1, t.length() - 1);
                BigDecimal value = variables.get(id);
                if (value == null) throw new IllegalArgumentException("找不到變數的值: " + id);
                return value;
            }
            try {
                return new BigDecimal(t);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("無法辨識的數字: " + t);
            }
        }

        private boolean isMul(String t) { return "*".equals(t) || "×".equals(t); }
        private boolean isDiv(String t) { return "/".equals(t) || "÷".equals(t); }
    }
}
