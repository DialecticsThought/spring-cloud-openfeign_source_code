/*
 * Decompiled with CFR 0.152.
 */
package feign.template;

import feign.Param;
import feign.Util;
import feign.template.Expression;
import feign.template.UriUtils;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Expressions {
    private static final String PATH_STYLE_MODIFIER = ";";
    private static final Pattern EXPRESSION_PATTERN = Pattern.compile("^([+#./;?&]?)(.*)$");

    public static Expression create(String value) {
        String expression = Expressions.stripBraces(value);
        if (expression == null || expression.isEmpty()) {
            throw new IllegalArgumentException("an expression is required.");
        }
        String variableName = null;
        String variablePattern = null;
        String modifier = null;
        Matcher matcher = EXPRESSION_PATTERN.matcher(expression);
        if (matcher.matches()) {
            modifier = matcher.group(1).trim();
            variableName = matcher.group(2).trim();
            if (variableName.contains(":")) {
                String[] parts = variableName.split(":");
                variableName = parts[0];
                variablePattern = parts[1];
            }
            if (variableName.contains("{")) {
                return null;
            }
        }
        if (PATH_STYLE_MODIFIER.equalsIgnoreCase(modifier)) {
            return new PathStyleExpression(variableName, variablePattern);
        }
        return new SimpleExpression(variableName, variablePattern);
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

    public static class PathStyleExpression
    extends SimpleExpression
    implements Param.Expander {
        public PathStyleExpression(String name, String pattern) {
            super(name, pattern, Expressions.PATH_STYLE_MODIFIER, true);
        }

        @Override
        protected String expand(Object variable, boolean encode) {
            return this.separator + super.expand(variable, encode);
        }

        @Override
        public String expand(Object value) {
            return this.expand(value, true);
        }

        @Override
        public String getValue() {
            if (this.getPattern() != null) {
                return "{" + this.separator + this.getName() + ":" + this.getName() + "}";
            }
            return "{" + this.separator + this.getName() + "}";
        }
    }

    static class SimpleExpression
    extends Expression {
        private static final String DEFAULT_SEPARATOR = ",";
        protected String separator = ",";
        private boolean nameRequired = false;

        SimpleExpression(String name, String pattern) {
            super(name, pattern);
        }

        SimpleExpression(String name, String pattern, String separator, boolean nameRequired) {
            this(name, pattern);
            this.separator = separator;
            this.nameRequired = nameRequired;
        }

        protected String encode(Object value) {
            return UriUtils.encode(value.toString(), Util.UTF_8);
        }

        @Override
        protected String expand(Object variable, boolean encode) {
            StringBuilder expanded = new StringBuilder();
            if (Iterable.class.isAssignableFrom(variable.getClass())) {
                expanded.append(this.expandIterable((Iterable)variable));
            } else if (Map.class.isAssignableFrom(variable.getClass())) {
                expanded.append(this.expandMap((Map)variable));
            } else {
                if (this.nameRequired) {
                    expanded.append(this.encode(this.getName())).append("=");
                }
                expanded.append(encode ? this.encode(variable) : variable);
            }
            String result = expanded.toString();
            if (!this.matches(result)) {
                throw new IllegalArgumentException("Value " + expanded + " does not match the expression pattern: " + this.getPattern());
            }
            return result;
        }

        protected String expandIterable(Iterable<?> values) {
            StringBuilder result = new StringBuilder();
            for (Object value : values) {
                if (value == null) continue;
                String expanded = this.encode(value);
                if (expanded.isEmpty()) {
                    result.append(this.separator);
                    continue;
                }
                if (result.length() != 0 && !result.toString().equalsIgnoreCase(this.separator)) {
                    result.append(this.separator);
                }
                if (this.nameRequired) {
                    result.append(this.encode(this.getName())).append("=");
                }
                result.append(expanded);
            }
            return result.toString();
        }

        protected String expandMap(Map<String, ?> values) {
            StringBuilder result = new StringBuilder();
            for (Map.Entry<String, ?> entry : values.entrySet()) {
                StringBuilder expanded = new StringBuilder();
                String name = this.encode(entry.getKey());
                String value = this.encode(entry.getValue().toString());
                expanded.append(name).append("=");
                if (!value.isEmpty()) {
                    expanded.append(value);
                }
                if (result.length() != 0) {
                    result.append(this.separator);
                }
                result.append((CharSequence)expanded);
            }
            return result.toString();
        }
    }
}

