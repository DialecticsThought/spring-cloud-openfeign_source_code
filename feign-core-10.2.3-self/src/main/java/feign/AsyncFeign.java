/*
 * Decompiled with CFR 0.152.
 */
package feign;

import feign.AsyncClient;
import feign.AsyncContextSupplier;
import feign.AsyncInvocation;
import feign.AsyncJoinException;
import feign.AsyncResponseHandler;
import feign.BaseBuilder;
import feign.Capability;
import feign.Client;
import feign.Contract;
import feign.Experimental;
import feign.Feign;
import feign.InvocationHandlerFactory;
import feign.Logger;
import feign.QueryMapEncoder;
import feign.ReflectiveAsyncFeign;
import feign.Request;
import feign.RequestInterceptor;
import feign.Response;
import feign.ResponseMapper;
import feign.Target;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Experimental
public abstract class AsyncFeign<C>
extends Feign {
    private final Feign feign;
    private AsyncContextSupplier<C> defaultContextSupplier;

    public static <C> AsyncBuilder<C> asyncBuilder() {
        return new AsyncBuilder();
    }

    protected AsyncFeign(Feign feign, AsyncContextSupplier<C> defaultContextSupplier) {
        this.feign = feign;
        this.defaultContextSupplier = defaultContextSupplier;
    }

    @Override
    public <T> T newInstance(Target<T> target) {
        return this.newInstance(target, this.defaultContextSupplier.newContext());
    }

    public <T> T newInstance(Target<T> target, C context) {
        return this.wrap(target.type(), this.feign.newInstance(target), context);
    }

    protected abstract <T> T wrap(Class<T> var1, T var2, C var3);

    public static class AsyncBuilder<C>
    extends BaseBuilder<AsyncBuilder<C>> {
        private AsyncContextSupplier<C> defaultContextSupplier = () -> null;
        private AsyncClient<C> client = new AsyncClient.Default(new Client.Default(null, null), LazyInitializedExecutorService.access$000());

        @Deprecated
        public AsyncBuilder<C> defaultContextSupplier(Supplier<C> supplier) {
            this.defaultContextSupplier = supplier::get;
            return this;
        }

        public AsyncBuilder<C> client(AsyncClient<C> client) {
            this.client = client;
            return this;
        }

        @Override
        public AsyncBuilder<C> mapAndDecode(ResponseMapper mapper, Decoder decoder) {
            return (AsyncBuilder)super.mapAndDecode(mapper, decoder);
        }

        @Override
        public AsyncBuilder<C> decoder(Decoder decoder) {
            return (AsyncBuilder)super.decoder(decoder);
        }

        @Override
        @Deprecated
        public AsyncBuilder<C> decode404() {
            return (AsyncBuilder)super.decode404();
        }

        @Override
        public AsyncBuilder<C> dismiss404() {
            return (AsyncBuilder)super.dismiss404();
        }

        @Override
        public AsyncBuilder<C> errorDecoder(ErrorDecoder errorDecoder) {
            return (AsyncBuilder)super.errorDecoder(errorDecoder);
        }

        @Override
        public AsyncBuilder<C> doNotCloseAfterDecode() {
            return (AsyncBuilder)super.doNotCloseAfterDecode();
        }

        public AsyncBuilder<C> defaultContextSupplier(AsyncContextSupplier<C> supplier) {
            this.defaultContextSupplier = supplier;
            return this;
        }

        public <T> T target(Class<T> apiType, String url) {
            return this.target(new Target.HardCodedTarget<T>(apiType, url));
        }

        public <T> T target(Class<T> apiType, String url, C context) {
            return this.target(new Target.HardCodedTarget<T>(apiType, url), context);
        }

        public <T> T target(Target<T> target) {
            return this.build().newInstance(target);
        }

        public <T> T target(Target<T> target, C context) {
            return this.build().newInstance(target, context);
        }

        @Override
        public AsyncBuilder<C> logLevel(Logger.Level logLevel) {
            return (AsyncBuilder)super.logLevel(logLevel);
        }

        @Override
        public AsyncBuilder<C> contract(Contract contract) {
            return (AsyncBuilder)super.contract(contract);
        }

        @Override
        public AsyncBuilder<C> logger(Logger logger) {
            return (AsyncBuilder)super.logger(logger);
        }

        @Override
        public AsyncBuilder<C> encoder(Encoder encoder) {
            return (AsyncBuilder)super.encoder(encoder);
        }

        @Override
        public AsyncBuilder<C> queryMapEncoder(QueryMapEncoder queryMapEncoder) {
            return (AsyncBuilder)super.queryMapEncoder(queryMapEncoder);
        }

        @Override
        public AsyncBuilder<C> options(Request.Options options) {
            return (AsyncBuilder)super.options(options);
        }

        @Override
        public AsyncBuilder<C> requestInterceptor(RequestInterceptor requestInterceptor) {
            return (AsyncBuilder)super.requestInterceptor(requestInterceptor);
        }

        @Override
        public AsyncBuilder<C> requestInterceptors(Iterable<RequestInterceptor> requestInterceptors) {
            return (AsyncBuilder)super.requestInterceptors(requestInterceptors);
        }

        @Override
        public AsyncBuilder<C> invocationHandlerFactory(InvocationHandlerFactory invocationHandlerFactory) {
            return (AsyncBuilder)super.invocationHandlerFactory(invocationHandlerFactory);
        }

        public AsyncFeign<C> build() {
            super.enrich();
            ThreadLocal<AsyncInvocation<C>> activeContextHolder = new ThreadLocal<AsyncInvocation<C>>();
            AsyncResponseHandler responseHandler = (AsyncResponseHandler)Capability.enrich(new AsyncResponseHandler(this.logLevel, this.logger, this.decoder, this.errorDecoder, this.dismiss404, this.closeAfterDecode, this.responseInterceptor), AsyncResponseHandler.class, this.capabilities);
            return new ReflectiveAsyncFeign<C>(((Builder)Feign.builder().logLevel(this.logLevel).client(this.stageExecution(activeContextHolder, this.client)).decoder(this.stageDecode(activeContextHolder, this.logger, this.logLevel, responseHandler)).forceDecoding().contract(this.contract).logger(this.logger).encoder(this.encoder).queryMapEncoder(this.queryMapEncoder).options(this.options).requestInterceptors((Iterable)this.requestInterceptors).responseInterceptor(this.responseInterceptor)).invocationHandlerFactory(this.invocationHandlerFactory).build(), this.defaultContextSupplier, activeContextHolder);
        }

        private Client stageExecution(ThreadLocal<AsyncInvocation<C>> activeContext, AsyncClient<C> client) {
            return (request, options) -> {
                Response result = Response.builder().status(200).request(request).build();
                AsyncInvocation invocationContext = (AsyncInvocation)activeContext.get();
                invocationContext.setResponseFuture(client.execute(request, options, Optional.ofNullable(invocationContext.context())));
                return result;
            };
        }

        long elapsedTime(long start) {
            return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        }

        private Decoder stageDecode(ThreadLocal<AsyncInvocation<C>> activeContext, Logger logger, Logger.Level logLevel, AsyncResponseHandler responseHandler) {
            return (response, type) -> {
                AsyncInvocation invocationContext = (AsyncInvocation)activeContext.get();
                CompletableFuture result = new CompletableFuture();
                invocationContext.responseFuture().whenComplete((r, t) -> {
                    long elapsedTime = this.elapsedTime(invocationContext.startNanos());
                    if (t != null) {
                        if (logLevel != Logger.Level.NONE && t instanceof IOException) {
                            IOException e = (IOException)t;
                            logger.logIOException(invocationContext.configKey(), logLevel, e, elapsedTime);
                        }
                        result.completeExceptionally((Throwable)t);
                    } else {
                        responseHandler.handleResponse(result, invocationContext.configKey(), (Response)r, invocationContext.underlyingType(), elapsedTime);
                    }
                });
                result.whenComplete((r, t) -> {
                    if (result.isCancelled()) {
                        invocationContext.responseFuture().cancel(true);
                    }
                });
                if (invocationContext.isAsyncReturnType()) {
                    return result;
                }
                try {
                    return result.join();
                }
                catch (CompletionException e) {
                    Response r2 = invocationContext.responseFuture().join();
                    Throwable cause = e.getCause();
                    if (cause == null) {
                        cause = e;
                    }
                    throw new AsyncJoinException(r2.status(), cause.getMessage(), r2.request(), cause);
                }
            };
        }
    }

    private static class LazyInitializedExecutorService {
        private static final ExecutorService instance = Executors.newCachedThreadPool(r -> {
            Thread result = new Thread(r);
            result.setDaemon(true);
            return result;
        });

        private LazyInitializedExecutorService() {
        }

        static /* synthetic */ ExecutorService access$000() {
            return instance;
        }
    }
}

