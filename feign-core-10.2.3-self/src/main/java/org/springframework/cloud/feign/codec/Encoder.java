/*
 * Decompiled with CFR 0.152.
 */
package org.springframework.cloud.feign.codec;

import feign.RequestTemplate;
import feign.Util;
import feign.codec.EncodeException;
import java.lang.reflect.Type;

public interface Encoder {
    public static final Type MAP_STRING_WILDCARD = Util.MAP_STRING_WILDCARD;

    public void encode(Object var1, Type var2, RequestTemplate var3) throws EncodeException;

    public static class Default
    implements Encoder {
        @Override
        public void encode(Object object, Type bodyType, RequestTemplate template) {
            if (bodyType == String.class) {
                template.body(object.toString());
            } else if (bodyType == byte[].class) {
                template.body((byte[])object, null);
            } else if (object != null) {
                throw new EncodeException(String.format("%s is not a type supported by this encoder.", object.getClass()));
            }
        }
    }
}

