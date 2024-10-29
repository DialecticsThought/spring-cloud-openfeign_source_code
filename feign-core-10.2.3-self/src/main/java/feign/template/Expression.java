/*
 * Decompiled with CFR 0.152.
 */
package feign.template;

import feign.template.TemplateChunk;
import java.util.Optional;
import java.util.regex.Pattern;

abstract class Expression
implements TemplateChunk {
    private String name;
    private Pattern pattern;

    Expression(String name, String pattern) {
        this.name = name;
        Optional.ofNullable(pattern).ifPresent(s -> {
            this.pattern = Pattern.compile(s);
        });
    }

    abstract String expand(Object var1, boolean var2);

    public String getName() {
        return this.name;
    }

    Pattern getPattern() {
        return this.pattern;
    }

    boolean matches(String value) {
        if (this.pattern == null) {
            return true;
        }
        return this.pattern.matcher(value).matches();
    }

    @Override
    public String getValue() {
        if (this.pattern != null) {
            return "{" + this.name + ":" + this.pattern + "}";
        }
        return "{" + this.name + "}";
    }

    public String toString() {
        return this.getValue();
    }
}

