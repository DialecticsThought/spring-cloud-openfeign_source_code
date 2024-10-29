/*
 * Decompiled with CFR 0.152.
 */
package feign.auth;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import feign.Util;
import feign.auth.Base64;
import java.nio.charset.Charset;

public class BasicAuthRequestInterceptor
implements RequestInterceptor {
    private final String headerValue;

    public BasicAuthRequestInterceptor(String username, String password) {
        this(username, password, Util.ISO_8859_1);
    }

    public BasicAuthRequestInterceptor(String username, String password, Charset charset) {
        Util.checkNotNull(username, "username", new Object[0]);
        Util.checkNotNull(password, "password", new Object[0]);
        this.headerValue = "Basic " + BasicAuthRequestInterceptor.base64Encode((username + ":" + password).getBytes(charset));
    }

    private static String base64Encode(byte[] bytes) {
        return Base64.encode(bytes);
    }

    @Override
    public void apply(RequestTemplate template) {
        template.header("Authorization", this.headerValue);
    }
}

