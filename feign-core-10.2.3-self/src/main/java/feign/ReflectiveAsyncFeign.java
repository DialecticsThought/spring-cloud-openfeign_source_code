/*
 * Decompiled with CFR 0.152.
 */
package feign;

import feign.AsyncContextSupplier;
import feign.AsyncFeign;
import feign.AsyncInvocation;
import feign.AsyncJoinException;
import feign.Experimental;
import feign.Feign;
import feign.MethodInfo;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Experimental
public class ReflectiveAsyncFeign<C>
extends AsyncFeign<C> {
    private ThreadLocal<AsyncInvocation<C>> activeContextHolder;

    public ReflectiveAsyncFeign(Feign feign, AsyncContextSupplier<C> defaultContextSupplier, ThreadLocal<AsyncInvocation<C>> contextHolder) {
        super(feign, defaultContextSupplier);
        this.activeContextHolder = contextHolder;
    }

    protected void setInvocationContext(AsyncInvocation<C> invocationContext) {
        this.activeContextHolder.set(invocationContext);
    }

    protected void clearInvocationContext() {
        this.activeContextHolder.remove();
    }

    private String getFullMethodName(Class<?> type, Type retType, Method m) {
        return retType.getTypeName() + " " + type.toGenericString() + "." + m.getName();
    }

    @Override
    protected <T> T wrap(Class<T> type, T instance, C context) {
        if (!type.isInterface()) {
            throw new IllegalArgumentException("Type must be an interface: " + type);
        }
        for (Method m : type.getMethods()) {
            Class<?> retType = m.getReturnType();
            if (!CompletableFuture.class.isAssignableFrom(retType)) continue;
            if (retType != CompletableFuture.class) {
                throw new IllegalArgumentException("Method return type is not CompleteableFuture: " + this.getFullMethodName(type, retType, m));
            }
            Type genRetType = m.getGenericReturnType();
            if (!ParameterizedType.class.isInstance(genRetType)) {
                throw new IllegalArgumentException("Method return type is not parameterized: " + this.getFullMethodName(type, genRetType, m));
            }
            if (!WildcardType.class.isInstance(((ParameterizedType)ParameterizedType.class.cast(genRetType)).getActualTypeArguments()[0])) continue;
            throw new IllegalArgumentException("Wildcards are not supported for return-type parameters: " + this.getFullMethodName(type, genRetType, m));
        }
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class[]{type}, new AsyncFeignInvocationHandler<T>(type, instance, context)));
    }

    private class AsyncFeignInvocationHandler<T>
    implements InvocationHandler {
        private final Map<Method, MethodInfo> methodInfoLookup = new ConcurrentHashMap<Method, MethodInfo>();
        private final Class<T> type;
        private final T instance;
        private final C context;

        AsyncFeignInvocationHandler(Class<T> type, T instance, C context) {
            this.type = type;
            this.instance = instance;
            this.context = context;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if ("equals".equals(method.getName()) && method.getParameterCount() == 1) {
                try {
                    InvocationHandler otherHandler = args.length > 0 && args[0] != null ? Proxy.getInvocationHandler(args[0]) : null;
                    return this.equals(otherHandler);
                }
                catch (IllegalArgumentException e) {
                    return false;
                }
            }
            if ("hashCode".equals(method.getName()) && method.getParameterCount() == 0) {
                return this.hashCode();
            }
            if ("toString".equals(method.getName()) && method.getParameterCount() == 0) {
                return this.toString();
            }
            MethodInfo methodInfo = this.methodInfoLookup.computeIfAbsent(method, m -> new MethodInfo(this.type, (Method)m));
            ReflectiveAsyncFeign.this.setInvocationContext(new AsyncInvocation(this.context, methodInfo));
            try {
                Object object = method.invoke(this.instance, args);
                return object;
            }
            catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof AsyncJoinException) {
                    cause = cause.getCause();
                }
                throw cause;
            }
            finally {
                ReflectiveAsyncFeign.this.clearInvocationContext();
            }
        }

        public boolean equals(Object obj) {
            if (obj instanceof AsyncFeignInvocationHandler) {
                AsyncFeignInvocationHandler other = (AsyncFeignInvocationHandler)obj;
                return this.instance.equals(other.instance);
            }
            return false;
        }

        public int hashCode() {
            return this.instance.hashCode();
        }

        public String toString() {
            return this.instance.toString();
        }
    }
}

