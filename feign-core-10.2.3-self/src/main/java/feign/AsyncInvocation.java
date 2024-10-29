/*
 * Decompiled with CFR 0.152.
 */
package feign;

import feign.Experimental;
import feign.MethodInfo;
import feign.Response;
import java.lang.reflect.Type;
import java.util.concurrent.CompletableFuture;

@Experimental
class AsyncInvocation<C> {
    private final C context;
    private final MethodInfo methodInfo;
    private final long startNanos;
    private CompletableFuture<Response> responseFuture;

    AsyncInvocation(C context, MethodInfo methodInfo) {
        this.context = context;
        this.methodInfo = methodInfo;
        this.startNanos = System.nanoTime();
    }

    C context() {
        return this.context;
    }

    String configKey() {
        return this.methodInfo.configKey();
    }

    long startNanos() {
        return this.startNanos;
    }

    Type underlyingType() {
        return this.methodInfo.underlyingReturnType();
    }

    boolean isAsyncReturnType() {
        return this.methodInfo.isAsyncReturnType();
    }

    void setResponseFuture(CompletableFuture<Response> responseFuture) {
        this.responseFuture = responseFuture;
    }

    CompletableFuture<Response> responseFuture() {
        return this.responseFuture;
    }
}

