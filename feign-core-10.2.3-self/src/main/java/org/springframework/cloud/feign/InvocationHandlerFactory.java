/*
 * Decompiled with CFR 0.152.
 */
package feign;

import feign.ReflectiveFeign;
import feign.Target;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Map;

public interface InvocationHandlerFactory {
    public InvocationHandler create(Target var1, Map<Method, MethodHandler> var2);

    public static final class Default
    implements InvocationHandlerFactory {
        @Override
        public InvocationHandler create(Target target, Map<Method, MethodHandler> dispatch) {
            return new ReflectiveFeign.FeignInvocationHandler(target, dispatch);
        }
    }

    public static interface MethodHandler {
        public Object invoke(Object[] var1) throws Throwable;
    }
}

