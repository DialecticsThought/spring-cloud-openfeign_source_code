/*
 * Decompiled with CFR 0.152.
 */
package feign.template;

import feign.Util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Expressions {
    private static Map<Pattern, Class<? extends Expression>> expressions = new LinkedHashMap<Pattern, Class<? extends Expression>>();

    public static Expression create(String value, UriUtils.FragmentType type) {
        String expression = Expressions.stripBraces(value);
        if (expression == null || expression.isEmpty()) {
            throw new IllegalArgumentException("an expression is required.");
        }
        Optional<Map.Entry> matchedExpressionEntry = expressions.entrySet().stream().filter(entry -> ((Pattern)entry.getKey()).matcher(expression).matches()).findFirst();
        if (!matchedExpressionEntry.isPresent()) {
            return null;
        }
        Map.Entry matchedExpression = matchedExpressionEntry.get();
        Pattern expressionPattern = (Pattern)matchedExpression.getKey();
        String variableName = null;
        String variablePattern = null;
        Matcher matcher = expressionPattern.matcher(expression);
        if (matcher.matches()) {
            variableName = matcher.group(1).trim();
            if (matcher.group(2) != null && matcher.group(3) != null) {
                variablePattern = matcher.group(3);
            }
        }
        return new SimpleExpression(variableName, variablePattern, type);
    }

    private static String stripBraces(String expression) {
        if (expression == null) {
            return null;
        }
        if (expression.startsWith("{") && expression.endsWith("}")) {
            return expression.substring(1, expression.length() - 1);
        }
        return expression;
    }

    static {
        expressions.put(Pattern.compile("(\\w[-\\w.\\[\\]]*[ ]*)(:(.+))?"), SimpleExpression.class);
    }

    static class SimpleExpression
    extends Expression {
        private final UriUtils.FragmentType type;

        SimpleExpression(String expression, String pattern, UriUtils.FragmentType type) {
            super(expression, pattern);
            this.type = type;
        }

        String encode(Object value) {
            return UriUtils.encodeReserved(value.toString(), this.type, Util.UTF_8);
        }

        @Override
        String expand(Object variable, boolean encode) {
            StringBuilder expanded = new StringBuilder();
            if (Iterable.class.isAssignableFrom(variable.getClass())) {
                ArrayList<String> items = new ArrayList<String>();
                for (Object item : (Iterable)variable) {
                    items.add(encode ? this.encode(item) : item.toString());
                }
                expanded.append(String.join((CharSequence)";", items));
            } else {
                expanded.append(encode ? this.encode(variable) : variable);
            }
            String result = expanded.toString();
            if (!this.matches(result)) {
                throw new IllegalArgumentException("Value " + expanded + " does not match the expression pattern: " + this.getPattern());
            }
            return result;
        }
    }
}

