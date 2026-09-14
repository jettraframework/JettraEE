package io.jettra.server.db.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

public class JettraQueryParser {

    public static <T> Predicate<T> parse(String queryStr, Class<T> clazz) {
        if (queryStr == null || queryStr.trim().isEmpty()) {
            return x -> true;
        }

        List<String> tokens = tokenize(queryStr);
        if (tokens.isEmpty()) {
            return x -> true;
        }

        Parser<T> parser = new Parser<>(tokens, clazz);
        return parser.parseExpression();
    }

    private static List<String> tokenize(String queryStr) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        char quoteChar = 0;

        for (int i = 0; i < queryStr.length(); i++) {
            char c = queryStr.charAt(i);
            if (inQuotes) {
                sb.append(c);
                if (c == quoteChar) {
                    inQuotes = false;
                    tokens.add(sb.toString());
                    sb.setLength(0);
                }
            } else {
                if (c == '\'' || c == '"') {
                    if (sb.length() > 0) {
                        tokens.add(sb.toString());
                        sb.setLength(0);
                    }
                    inQuotes = true;
                    quoteChar = c;
                    sb.append(c);
                } else if (c == '(' || c == ')') {
                    if (sb.length() > 0) {
                        tokens.add(sb.toString());
                        sb.setLength(0);
                    }
                    tokens.add(String.valueOf(c));
                } else if (Character.isWhitespace(c)) {
                    if (sb.length() > 0) {
                        tokens.add(sb.toString());
                        sb.setLength(0);
                    }
                } else if (c == '=' || c == '!' || c == '<' || c == '>') {
                    if (sb.length() > 0) {
                        tokens.add(sb.toString());
                        sb.setLength(0);
                    }
                    sb.append(c);
                    if (i + 1 < queryStr.length()) {
                        char next = queryStr.charAt(i + 1);
                        if (next == '=') {
                            sb.append(next);
                            i++;
                        }
                    }
                    tokens.add(sb.toString());
                    sb.setLength(0);
                } else {
                    sb.append(c);
                }
            }
        }
        if (sb.length() > 0) {
            tokens.add(sb.toString());
        }
        return tokens;
    }

    private static class Parser<T> {
        private final List<String> tokens;
        private final Class<T> clazz;
        private int pos = 0;

        Parser(List<String> tokens, Class<T> clazz) {
            this.tokens = tokens;
            this.clazz = clazz;
        }

        private String peek() {
            return pos < tokens.size() ? tokens.get(pos) : null;
        }

        private String next() {
            return pos < tokens.size() ? tokens.get(pos++) : null;
        }

        Predicate<T> parseExpression() {
            Predicate<T> pred = parseTerm();
            while (true) {
                String t = peek();
                if ("OR".equalsIgnoreCase(t)) {
                    next(); // consume "OR"
                    Predicate<T> right = parseTerm();
                    Predicate<T> left = pred;
                    pred = x -> left.test(x) || right.test(x);
                } else {
                    break;
                }
            }
            return pred;
        }

        private Predicate<T> parseTerm() {
            Predicate<T> pred = parseFactor();
            while (true) {
                String t = peek();
                if ("AND".equalsIgnoreCase(t)) {
                    next(); // consume "AND"
                    Predicate<T> right = parseFactor();
                    Predicate<T> left = pred;
                    pred = x -> left.test(x) && right.test(x);
                } else {
                    break;
                }
            }
            return pred;
        }

        private Predicate<T> parseFactor() {
            String t = peek();
            if ("(".equals(t)) {
                next(); // consume "("
                Predicate<T> pred = parseExpression();
                String closing = next();
                if (!")".equals(closing)) {
                    throw new IllegalArgumentException("Expected ')' but found: " + closing);
                }
                return pred;
            }

            String fieldPath = next();
            if (fieldPath == null) {
                throw new IllegalArgumentException("Expected field path");
            }
            String operator = next();
            if (operator == null) {
                throw new IllegalArgumentException("Expected operator after field path: " + fieldPath);
            }
            String value = next();
            if (value == null) {
                throw new IllegalArgumentException("Expected value after operator: " + operator);
            }

            return x -> {
                Object val = getValueByPath(x, fieldPath);
                return evaluateComparison(val, operator, value);
            };
        }
    }

    public static Object getValueByPath(Object obj, String path) {
        if (obj == null) return null;
        int dotIndex = path.indexOf('.');
        if (dotIndex == -1) {
            return getSimpleValue(obj, path);
        }
        String head = path.substring(0, dotIndex);
        String tail = path.substring(dotIndex + 1);
        Object value = getSimpleValue(obj, head);
        if (value instanceof Collection<?>) {
            List<Object> results = new ArrayList<>();
            for (Object item : (Collection<?>) value) {
                Object res = getValueByPath(item, tail);
                if (res != null) {
                    if (res instanceof Collection<?>) {
                        results.addAll((Collection<?>) res);
                    } else {
                        results.add(res);
                    }
                }
            }
            return results;
        }
        return getValueByPath(value, tail);
    }

    private static Object getSimpleValue(Object obj, String field) {
        try {
            java.lang.reflect.Method method = obj.getClass().getMethod(field);
            return method.invoke(obj);
        } catch (Exception e) {
            for (java.lang.reflect.Method m : obj.getClass().getMethods()) {
                if (m.getName().equalsIgnoreCase(field) && m.getParameterCount() == 0) {
                    try {
                        return m.invoke(obj);
                    } catch (Exception ex) {
                        return null;
                    }
                }
            }
            return null;
        }
    }

    public static boolean evaluateComparison(Object fieldValue, String op, String constantValue) {
        if (fieldValue == null) {
            return op.equals("!=");
        }

        if (fieldValue instanceof Collection<?>) {
            for (Object item : (Collection<?>) fieldValue) {
                if (evaluateSingleComparison(item, op, constantValue)) {
                    return true;
                }
            }
            return false;
        }

        return evaluateSingleComparison(fieldValue, op, constantValue);
    }

    private static boolean evaluateSingleComparison(Object val, String op, String constantValue) {
        if (val == null) return false;

        String valStr = val.toString();
        String cleanConst = constantValue;
        if (cleanConst.startsWith("'") && cleanConst.endsWith("'")) {
            cleanConst = cleanConst.substring(1, cleanConst.length() - 1);
        } else if (cleanConst.startsWith("\"") && cleanConst.endsWith("\"")) {
            cleanConst = cleanConst.substring(1, cleanConst.length() - 1);
        }

        switch (op) {
            case "=":
            case "==":
                if (val instanceof Boolean) {
                    return val.equals(Boolean.valueOf(cleanConst));
                }
                return valStr.equalsIgnoreCase(cleanConst);
            case "!=":
                if (val instanceof Boolean) {
                    return !val.equals(Boolean.valueOf(cleanConst));
                }
                return !valStr.equalsIgnoreCase(cleanConst);
            case "contains":
                return valStr.toLowerCase().contains(cleanConst.toLowerCase());
            case "startsWith":
                return valStr.toLowerCase().startsWith(cleanConst.toLowerCase());
            case "endsWith":
                return valStr.toLowerCase().endsWith(cleanConst.toLowerCase());
            default:
                return false;
        }
    }
}
