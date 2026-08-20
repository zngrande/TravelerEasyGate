package com.example.UsefulTravel.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * FormulaEngine - 安全的四則運算公式引擎, 給「計算公式」功能用 (取代原本寫死的加成% 計算)。
 *
 * 公式字串格式範例:
 *   "{NET_COST} * 1.15 + 2000"
 *   "({NET_COST} + 500) / 0.9"
 *   "{TRADE_PRICE} * 0.08"
 *
 * 變數用大括號包起來 (例如 {NET_COST}、{GROUP_SIZE}、{TRADE_PRICE})，
 * 呼叫端傳入 Map<String, BigDecimal> 決定每個變數目前的值。
 *
 * 這是自己刻的遞迴下降解析器, 不是用 eval/腳本引擎, 只認得數字、+ - * / ( ) 跟 {VAR}，
 * 其餘字元一律視為格式錯誤, 不會有注入風險。
 */
public final class FormulaEngine {

    // 一個 token 只會是: {變數}、數字(含小數)、運算子、括號、或空白 (空白會被丟掉)
    private static final Pattern TOKEN_PATTERN = Pattern.compile(
            "\\{[A-Za-z0-9_]+\\}|\\d+(\\.\\d+)?|[+\\-*/()]|\\s+");

    private FormulaEngine() {}

    public static class FormulaException extends RuntimeException {
        public FormulaException(String message) { super(message); }
    }

    /** 純粹驗證格式 + 變數是否都能算出來, 存檔前檢查用 (拿樣本數字跑一次, 錯了就丟例外)。 */
    public static void validate(String expression, Map<String, BigDecimal> sampleVariables) {
        evaluate(expression, sampleVariables);
    }

    public static BigDecimal evaluate(String expression, Map<String, BigDecimal> variables) {
        if (expression == null || expression.isBlank()) {
            throw new FormulaException("公式不能是空的");
        }
        // 保險: 前端有些地方 (乘號×/除號÷/全形負號−) 是用比較好看的 Unicode 符號顯示,
        // 這裡統一正規化成 ASCII 的 * / -，避免因為符號不同被誤判成「不合法字元」。
        String normalized = expression.replace('×', '*').replace('÷', '/').replace('−', '-');
        List<String> tokens = tokenize(normalized.trim());
        if (tokens.isEmpty()) {
            throw new FormulaException("公式不能是空的");
        }
        Parser parser = new Parser(tokens, variables == null ? Map.of() : variables);
        BigDecimal result = parser.parseExpression();
        if (!parser.isAtEnd()) {
            throw new FormulaException("公式格式錯誤（多出來的內容：「" + parser.peek() + "」）");
        }
        return result;
    }

    private static List<String> tokenize(String expression) {
        Matcher m = TOKEN_PATTERN.matcher(expression);
        List<String> tokens = new ArrayList<>();
        int pos = 0;
        while (pos < expression.length()) {
            if (!m.find(pos) || m.start() != pos) {
                throw new FormulaException("公式包含不合法的字元：「" + expression.charAt(pos) + "」");
            }
            String tok = m.group();
            pos = m.end();
            if (!tok.isBlank()) tokens.add(tok);
        }
        return tokens;
    }

    private static class Parser {
        private final List<String> tokens;
        private final Map<String, BigDecimal> variables;
        private int i = 0;

        Parser(List<String> tokens, Map<String, BigDecimal> variables) {
            this.tokens = tokens;
            this.variables = variables;
        }

        boolean isAtEnd() { return i >= tokens.size(); }
        String peek() { return isAtEnd() ? null : tokens.get(i); }
        String next() { return tokens.get(i++); }

        // expression := term (('+' | '-') term)*
        BigDecimal parseExpression() {
            BigDecimal value = parseTerm();
            while (!isAtEnd() && ("+".equals(peek()) || "-".equals(peek()))) {
                String op = next();
                BigDecimal rhs = parseTerm();
                value = "+".equals(op) ? value.add(rhs) : value.subtract(rhs);
            }
            return value;
        }

        // term := factor (('*' | '/') factor)*
        BigDecimal parseTerm() {
            BigDecimal value = parseFactor();
            while (!isAtEnd() && ("*".equals(peek()) || "/".equals(peek()))) {
                String op = next();
                BigDecimal rhs = parseFactor();
                if ("*".equals(op)) {
                    value = value.multiply(rhs);
                } else {
                    if (rhs.compareTo(BigDecimal.ZERO) == 0) throw new FormulaException("公式裡出現除以 0");
                    value = value.divide(rhs, 10, RoundingMode.HALF_UP);
                }
            }
            return value;
        }

        // factor := ('+' | '-') factor | '(' expression ')' | number | '{' VAR '}'
        BigDecimal parseFactor() {
            if (isAtEnd()) throw new FormulaException("公式不完整");
            String tok = peek();
            if ("-".equals(tok)) {
                next();
                return parseFactor().negate();
            }
            if ("+".equals(tok)) {
                next();
                return parseFactor();
            }
            if ("(".equals(tok)) {
                next();
                BigDecimal value = parseExpression();
                if (isAtEnd() || !")".equals(next())) throw new FormulaException("括號沒有對齊");
                return value;
            }
            if (tok.startsWith("{")) {
                next();
                String varName = tok.substring(1, tok.length() - 1);
                BigDecimal value = variables.get(varName);
                if (value == null) {
                    throw new FormulaException("公式引用了「" + varName + "」，但這個階段還沒有這個變數的值（例如直售價公式不能引用還沒算出來的退傭金額）");
                }
                return value;
            }
            next();
            try {
                return new BigDecimal(tok);
            } catch (NumberFormatException e) {
                throw new FormulaException("公式格式錯誤：「" + tok + "」");
            }
        }
    }
}
