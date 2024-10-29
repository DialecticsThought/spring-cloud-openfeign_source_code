/*
 * Decompiled with CFR 0.152.
 */
package feign;

import feign.Experimental;
import feign.Feign;
import feign.Types;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.concurrent.CompletableFuture;

@Experimental
class MethodInfo {
    private final String configKey;
    private final Type underlyingReturnType;
    private final boolean asyncReturnType;

    MethodInfo(String configKey, Type underlyingReturnType, boolean asyncReturnType) {
        this.configKey = configKey;
        this.underlyingReturnType = underlyingReturnType;
        this.asyncReturnType = asyncReturnType;
    }

    MethodInfo(Class<?> targetType, Method method) {
        this.configKey = Feign.configKey(targetType, method);
        Type type = Types.resolve(targetType, targetType, method.getGenericReturnType());
        if (type instanceof ParameterizedType && Types.getRawType(type).isAssignableFrom(CompletableFuture.class)) {
            this.asyncReturnType = true;
            this.underlyingReturnType = ((ParameterizedType)type).getActualTypeArguments()[0];
        } else {
            this.asyncReturnType = false;
            this.underlyingReturnType = type;
        }
    }

    String configKey() {
        return this.configKey;
    }

    Type underlyingReturnType() {
        return this.underlyingReturnType;
    }

    boolean isAsyncReturnType() {
        return this.asyncReturnType;
    }
}

