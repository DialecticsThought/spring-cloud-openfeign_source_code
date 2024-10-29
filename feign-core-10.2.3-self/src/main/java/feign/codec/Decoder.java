/*
 * Decompiled with CFR 0.152.
 */
package feign.codec;

import feign.FeignException;
import feign.Response;
import feign.Util;

import java.io.IOException;
import java.lang.reflect.Type;

public interface Decoder {
    public Object decode(Response var1, Type var2) throws IOException, DecodeException, FeignException;

    public static class Default
    extends StringDecoder {
        @Override
        public Object decode(Response response, Type type) throws IOException {
            if (response.status() == 404 || response.status() == 204) {
                return Util.emptyValueOf(type);
            }
            if (response.body() == null) {
                return null;
            }
            if (byte[].class.equals((Object)type)) {
                return Util.toByteArray(response.body().asInputStream());
            }
            return super.decode(response, type);
        }
    }
}

