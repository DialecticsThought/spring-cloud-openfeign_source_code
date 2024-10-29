/*
 * Decompiled with CFR 0.152.
 */
package feign;

import feign.template.UriUtils;
import java.nio.charset.Charset;
import java.util.Collection;

public enum CollectionFormat {
    CSV(","),
    SSV(" "),
    TSV("\t"),
    PIPES("|"),
    EXPLODED(null);

    private final String separator;

    private CollectionFormat(String separator) {
        this.separator = separator;
    }

    public CharSequence join(String field, Collection<String> values, Charset charset) {
        StringBuilder builder = new StringBuilder();
        int valueCount = 0;
        for (String value : values) {
            if (this.separator == null) {
                builder.append(valueCount++ == 0 ? "" : "&");
                builder.append(UriUtils.encode(field, charset));
                if (value == null) continue;
                builder.append('=');
                builder.append(value);
                continue;
            }
            if (builder.length() == 0) {
                builder.append(UriUtils.encode(field, charset));
            }
            if (value == null) continue;
            builder.append(valueCount++ == 0 ? "=" : UriUtils.encode(this.separator, charset));
            builder.append(value);
        }
        return builder;
    }
}

