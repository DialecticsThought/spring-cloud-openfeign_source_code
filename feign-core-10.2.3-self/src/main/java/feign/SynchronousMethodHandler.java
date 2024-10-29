/*
 * Decompiled with CFR 0.152.
 */
package feign;

import feign.AsyncResponseHandler;
import feign.Client;
import feign.ExceptionPropagationPolicy;
import feign.FeignException;
import feign.InvocationContext;
import feign.InvocationHandlerFactory;
import feign.Logger;
import feign.MethodMetadata;
import feign.Request;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import feign.Response;
import feign.ResponseInterceptor;
import feign.RetryableException;
import feign.Retryer;
import feign.Target;
import feign.Util;
import feign.codec.Decoder;
import feign.codec.ErrorDecoder;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

final class SynchronousMethodHandler
implements InvocationHandlerFactory.MethodHandler {
    private static final long MAX_RESPONSE_BUFFER_SIZE = 8192L;
    private final MethodMetadata metadata;
    private final Target<?> target;
    private final Client client;
    private final Retryer retryer;
    private final List<RequestInterceptor> requestInterceptors;
    private final ResponseInterceptor responseInterceptor;
    private final Logger logger;
    private final Logger.Level logLevel;
    private final RequestTemplate.Factory buildTemplateFromArgs;
    private final Request.Options options;
    private final ExceptionPropagationPolicy propagationPolicy;
    private final Decoder decoder;
    private final AsyncResponseHandler asyncResponseHandler;

    private SynchronousMethodHandler(Target<?> target, Client client, Retryer retryer, List<RequestInterceptor> requestInterceptors, ResponseInterceptor responseInterceptor, Logger logger, Logger.Level logLevel, MethodMetadata metadata, RequestTemplate.Factory buildTemplateFromArgs, Request.Options options, Decoder decoder, ErrorDecoder errorDecoder, boolean dismiss404, boolean closeAfterDecode, ExceptionPropagationPolicy propagationPolicy, boolean forceDecoding) {
        this.target = Util.checkNotNull(target, "target", new Object[0]);
        this.client = Util.checkNotNull(client, "client for %s", target);
        this.retryer = Util.checkNotNull(retryer, "retryer for %s", target);
        this.requestInterceptors = Util.checkNotNull(requestInterceptors, "requestInterceptors for %s", target);
        this.logger = Util.checkNotNull(logger, "logger for %s", target);
        this.logLevel = Util.checkNotNull(logLevel, "logLevel for %s", target);
        this.metadata = Util.checkNotNull(metadata, "metadata for %s", target);
        this.buildTemplateFromArgs = Util.checkNotNull(buildTemplateFromArgs, "metadata for %s", target);
        this.options = Util.checkNotNull(options, "options for %s", target);
        this.propagationPolicy = propagationPolicy;
        this.responseInterceptor = responseInterceptor;
        if (forceDecoding) {
            this.decoder = decoder;
            this.asyncResponseHandler = null;
        } else {
            this.decoder = null;
            this.asyncResponseHandler = new AsyncResponseHandler(logLevel, logger, decoder, errorDecoder, dismiss404, closeAfterDecode, responseInterceptor);
        }
    }

    @Override
    public Object invoke(Object[] argv) throws Throwable {
        RequestTemplate template = this.buildTemplateFromArgs.create(argv);
        Request.Options options = this.findOptions(argv);
        Retryer retryer = this.retryer.clone();
        while (true) {
            try {
                return this.executeAndDecode(template, options);
            }
            catch (RetryableException e) {
                try {
                    retryer.continueOrPropagate(e);
                }
                catch (RetryableException th) {
                    Throwable cause = th.getCause();
                    if (this.propagationPolicy == ExceptionPropagationPolicy.UNWRAP && cause != null) {
                        throw cause;
                    }
                    throw th;
                }
                if (this.logLevel == Logger.Level.NONE) continue;
                this.logger.logRetry(this.metadata.configKey(), this.logLevel);
                continue;
            }
            break;
        }
    }

    Object executeAndDecode(RequestTemplate template, Request.Options options) throws Throwable {
        Response response;
        Request request = this.targetRequest(template);
        if (this.logLevel != Logger.Level.NONE) {
            this.logger.logRequest(this.metadata.configKey(), this.logLevel, request);
        }
        long start = System.nanoTime();
        try {
            response = this.client.execute(request, options);
            response = response.toBuilder().request(request).requestTemplate(template).build();
        }
        catch (IOException e) {
            if (this.logLevel != Logger.Level.NONE) {
                this.logger.logIOException(this.metadata.configKey(), this.logLevel, e, this.elapsedTime(start));
            }
            throw FeignException.errorExecuting(request, e);
        }
        long elapsedTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        if (this.decoder != null) {
            return this.responseInterceptor.aroundDecode(new InvocationContext(this.decoder, this.metadata.returnType(), response));
        }
        CompletableFuture<Object> resultFuture = new CompletableFuture<Object>();
        this.asyncResponseHandler.handleResponse(resultFuture, this.metadata.configKey(), response, this.metadata.returnType(), elapsedTime);
        try {
            if (!resultFuture.isDone()) {
                throw new IllegalStateException("Response handling not done");
            }
            return resultFuture.join();
        }
        catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause != null) {
                throw cause;
            }
            throw e;
        }
    }

    long elapsedTime(long start) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }

    Request targetRequest(RequestTemplate template) {
        for (RequestInterceptor interceptor : this.requestInterceptors) {
            interceptor.apply(template);
        }
        return this.target.apply(template);
    }

    Request.Options findOptions(Object[] argv) {
        if (argv == null || argv.length == 0) {
            return this.options;
        }
        return Stream.of(argv).filter(Request.Options.class::isInstance).map(Request.Options.class::cast).findFirst().orElse(this.options);
    }

    static class Factory {
        private final Client client;
        private final Retryer retryer;
        private final List<RequestInterceptor> requestInterceptors;
        private final ResponseInterceptor responseInterceptor;
        private final Logger logger;
        private final Logger.Level logLevel;
        private final boolean dismiss404;
        private final boolean closeAfterDecode;
        private final ExceptionPropagationPolicy propagationPolicy;
        private final boolean forceDecoding;

        Factory(Client client, Retryer retryer, List<RequestInterceptor> requestInterceptors, ResponseInterceptor responseInterceptor, Logger logger, Logger.Level logLevel, boolean dismiss404, boolean closeAfterDecode, ExceptionPropagationPolicy propagationPolicy, boolean forceDecoding) {
            this.client = Util.checkNotNull(client, "client", new Object[0]);
            this.retryer = Util.checkNotNull(retryer, "retryer", new Object[0]);
            this.requestInterceptors = Util.checkNotNull(requestInterceptors, "requestInterceptors", new Object[0]);
            this.responseInterceptor = responseInterceptor;
            this.logger = Util.checkNotNull(logger, "logger", new Object[0]);
            this.logLevel = Util.checkNotNull(logLevel, "logLevel", new Object[0]);
            this.dismiss404 = dismiss404;
            this.closeAfterDecode = closeAfterDecode;
            this.propagationPolicy = propagationPolicy;
            this.forceDecoding = forceDecoding;
        }

        public InvocationHandlerFactory.MethodHandler create(Target<?> target, MethodMetadata md, RequestTemplate.Factory buildTemplateFromArgs, Request.Options options, Decoder decoder, ErrorDecoder errorDecoder) {
            return new SynchronousMethodHandler(target, this.client, this.retryer, this.requestInterceptors, this.responseInterceptor, this.logger, this.logLevel, md, buildTemplateFromArgs, options, decoder, errorDecoder, this.dismiss404, this.closeAfterDecode, this.propagationPolicy, this.forceDecoding);
        }
    }
}

