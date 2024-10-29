/*
 * Decompiled with CFR 0.152.
 */
package feign.codec;

import feign.Response;
import feign.Util;
import feign.codec.DecodeException;
import feign.codec.Decoder;
import java.io.IOException;
import java.lang.reflect.Type;

public class StringDecoder
implements Decoder {
    @Override
    public Object decode(Response response, Type type) throws IOException {
        Response.Body body = response.body();
        if (response.status() == 404 || response.status() == 204 || body == null) {
            return null;
        }
        if (String.class.equals((Object)type)) {
            return Util.toString(body.asReader(Util.UTF_8));
        }
        throw new DecodeException(response.status(), String.format("%s is not a type supported by this decoder.", type), response.request());
    }
}

