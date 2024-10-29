/*
 * Decompiled with CFR 0.152.
 */
package feign.template;

import feign.Util;

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

    private BodyTemplate(String value, Charset charset) {
        super(value, Template.ExpansionOptions.ALLOW_UNRESOLVED, Template.EncodingOptions.NOT_REQUIRED, false, charset);
        if (value.startsWith(JSON_TOKEN_START_ENCODED) && value.endsWith(JSON_TOKEN_END_ENCODED)) {
            this.json = true;
        }
    }

    @Override
    public String expand(Map<String, ?> variables) {
        String expanded = super.expand(variables);
        if (this.json) {
            StringBuilder sb = new StringBuilder();
            sb.append(JSON_TOKEN_START);
            sb.append(expanded, expanded.indexOf(JSON_TOKEN_START_ENCODED) + JSON_TOKEN_START_ENCODED.length(), expanded.lastIndexOf(JSON_TOKEN_END_ENCODED));
            sb.append(JSON_TOKEN_END);
            return sb.toString();
        }
        return expanded;
    }
}

