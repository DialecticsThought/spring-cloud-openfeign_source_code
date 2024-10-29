/*
 * Decompiled with CFR 0.152.
 */
package feign;

import feign.querymap.FieldQueryMapEncoder;
import java.util.Map;

public interface QueryMapEncoder {
    public Map<String, Object> encode(Object var1);

    public static class Default
    extends FieldQueryMapEncoder {
    }
}

