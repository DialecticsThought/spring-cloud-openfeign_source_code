/*
 * Decompiled with CFR 0.152.
 */
package org.springframework.cloud.feign.template;

import feign.template.TemplateChunk;

class Literal
implements TemplateChunk {
    private final String value;

    public static Literal create(String value) {
        return new Literal(value);
    }

    Literal(String value) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("a value is required.");
        }
        this.value = value;
    }

    @Override
    public String getValue() {
        return this.value;
    }
}

