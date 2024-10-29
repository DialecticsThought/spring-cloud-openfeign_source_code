/*
 * Decompiled with CFR 0.152.
 */
package feign.template;

import feign.Util;
import feign.template.Template;
import java.nio.charset.Charset;
import java.util.Map;

public final class BodyTemplate
extends Template {
    private static final String JSON_TOKEN_START = "{";
    private static final String JSON_TOKEN_END = "}";
    private static final String JSON_TOKEN_START_ENCODED = "%7B";
    private static final String JSON_TOKEN_END_ENCODED = "%7D";
    private boolean json = false;

    public static BodyTemplate create(String template) {
        return new BodyTemplate(template, Util.UTF_8);
    }

    public static BodyTemplate create(String template, Charset charset) {
        return new BodyTemplate(template, charset);
    }

    private BodyTemplate(String value, Charset charset) {
        super(value, ExpansionOptions.ALLOW_UNRESOLVED, EncodingOptions.NOT_REQUIRED, false, charset);
        if (value.startsWith(JSON_TOKEN_START_ENCODED) && value.endsWith(JSON_TOKEN_END_ENCODED)) {
            this.json = true;
        }
    }

    @Override
    public String expand(Map<String, ?> variables) {
        String expanded = super.expand(variables);
        if (this.json) {
            expanded = expanded.replaceAll(JSON_TOKEN_START_ENCODED, JSON_TOKEN_START);
            expanded = expanded.replaceAll(JSON_TOKEN_END_ENCODED, JSON_TOKEN_END);
        }
        return expanded;
    }
}

