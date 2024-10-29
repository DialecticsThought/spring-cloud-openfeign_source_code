/*
 * Decompiled with CFR 0.152.
 */
package feign;

import feign.Response;
import java.lang.reflect.Type;

public interface ResponseMapper {
    public Response map(Response var1, Type var2);
}

