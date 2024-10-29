/*
 * Decompiled with CFR 0.152.
 */
package feign;

import feign.InvocationContext;
import java.io.IOException;

public interface ResponseInterceptor {
    public static final ResponseInterceptor DEFAULT = InvocationContext::proceed;

    public Object aroundDecode(InvocationContext var1) throws IOException;
}

