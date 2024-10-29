/*
 * Decompiled with CFR 0.152.
 */
package feign;

import feign.Client;
import feign.ExceptionPropagationPolicy;
import feign.FeignException;
import feign.InvocationHandlerFactory;
import feign.Logger;
import feign.MethodMetadata;
import feign.Request;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import feign.Response;
import feign.RetryableException;
import feign.Retryer;
import feign.Target;
import feign.Util;
import feign.codec.DecodeException;
import feign.codec.Decoder;
import feign.codec.ErrorDecoder;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

final class SynchronousMethodHandler
implements InvocationHandlerFactory.MethodHandler {
    private static final long MAX_RESPONSE_BUFFER_SIZE = 8192L;
    private final MethodMetadata metadata;
    private final Target<?> target;
    private final Client client;
    private final Retryer retryer;
    private final List<RequestInterceptor> requestInterceptors;
    private final Logger logger;
    private final Logger.Level logLevel;
    private final RequestTemplate.Factory buildTemplateFromArgs;
    private final Request.Options options;
    private final Decoder decoder;
    private final ErrorDecoder errorDecoder;
    private final boolean decode404;
    private final boolean closeAfterDecode;
    private final ExceptionPropagationPolicy propagationPolicy;

    private SynchronousMethodHandler(Target<?> target, Client client, Retryer retryer, List<RequestInterceptor> requestInterceptors, Logger logger, Logger.Level logLevel, MethodMetadata metadata, RequestTemplate.Factory buildTemplateFromArgs, Request.Options options, Decoder decoder, ErrorDecoder errorDecoder, boolean decode404, boolean closeAfterDecode, ExceptionPropagationPolicy propagationPolicy) {
        this.target = Util.checkNotNull(target, "target", new Object[0]);
        this.client = Util.checkNotNull(client, "client for %s", target);
        this.retryer = Util.checkNotNull(retryer, "retryer for %s", target);
        this.requestInterceptors = Util.checkNotNull(requestInterceptors, "requestInterceptors for %s", target);
        this.logger = Util.checkNotNull(logger, "logger for %s", target);
        this.logLevel = Util.checkNotNull(logLevel, "logLevel for %s", target);
        this.metadata = Util.checkNotNull(metadata, "metadata for %s", target);
        this.buildTemplateFromArgs = Util.checkNotNull(buildTemplateFromArgs, "metadata for %s", target);
        this.options = Util.checkNotNull(options, "options for %s", target);
        this.errorDecoder = Util.checkNotNull(errorDecoder, "errorDecoder for %s", target);
        this.decoder = Util.checkNotNull(decoder, "decoder for %s", target);
        this.decode404 = decode404;
        this.closeAfterDecode = closeAfterDecode;
        this.propagationPolicy = propagationPolicy;
    }

    @Override
    public Object invoke(Object[] argv) throws Throwable {
        RequestTemplate template = this.buildTemplateFromArgs.create(argv);
        Retryer retryer = this.retryer.clone();
        while (true) {
            try {
                return this.executeAndDecode(template);
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

    Object executeAndDecode(RequestTemplate template) throws Throwable {
        Response response;
        Request request = this.targetRequest(template);
        if (this.logLevel != Logger.Level.NONE) {
            this.logger.logRequest(this.metadata.configKey(), this.logLevel, request);
        }
        long start = System.nanoTime();
        try {
            response = this.client.execute(request, this.options);
        }
        catch (IOException e) {
            if (this.logLevel != Logger.Level.NONE) {
                this.logger.logIOException(this.metadata.configKey(), this.logLevel, e, this.elapsedTime(start));
            }
            throw FeignException.errorExecuting(request, e);
        }
        long elapsedTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        boolean shouldClose = true;
        try {
            if (this.logLevel != Logger.Level.NONE) {
                response = this.logger.logAndRebufferResponse(this.metadata.configKey(), this.logLevel, response, elapsedTime);
            }
            if (Response.class == this.metadata.returnType()) {
                if (response.body() == null) {
                    Response response2 = response;
                    return response2;
                }
                if (response.body().length() == null || (long)response.body().length().intValue() > 8192L) {
                    shouldClose = false;
                    Response response3 = response;
                    return response3;
                }
                byte[] bodyData = Util.toByteArray(response.body().asInputStream());
                Response response4 = response.toBuilder().body(bodyData).build();
                return response4;
            }
            if (response.status() >= 200 && response.status() < 300) {
                if (Void.TYPE == this.metadata.returnType()) {
                    Object bodyData = null;
                    return bodyData;
                }
                Object result = this.decode(response);
                shouldClose = this.closeAfterDecode;
                Object object = result;
                return object;
            }
            if (this.decode404 && response.status() == 404 && Void.TYPE != this.metadata.returnType()) {
                Object result = this.decode(response);
                shouldClose = this.closeAfterDecode;
                Object object = result;
                return object;
            }
            try {
                throw this.errorDecoder.decode(this.metadata.configKey(), response);
            }
            catch (IOException e) {
                if (this.logLevel != Logger.Level.NONE) {
                    this.logger.logIOException(this.metadata.configKey(), this.logLevel, e, elapsedTime);
                }
                throw FeignException.errorReading(request, response, e);
            }
        }
        finally {
            if (shouldClose) {
                Util.ensureClosed(response.body());
            }
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

    Object decode(Response response) throws Throwable {
        try {
            return this.decoder.decode(response, this.metadata.returnType());
        }
        catch (FeignException e) {
            throw e;
        }
        catch (RuntimeException e) {
            throw new DecodeException(response.status(), e.getMessage(), e);
        }
    }

    static class Factory {
        private final Client client;
        private final Retryer retryer;
        private final List<RequestInterceptor> requestInterceptors;
        private final Logger logger;
        private final Logger.Level logLevel;
        private final boolean decode404;
        private final boolean closeAfterDecode;
        private final ExceptionPropagationPolicy propagationPolicy;

        Factory(Client client, Retryer retryer, List<RequestInterceptor> requestInterceptors, Logger logger, Logger.Level logLevel, boolean decode404, boolean closeAfterDecode, ExceptionPropagationPolicy propagationPolicy) {
            this.client = Util.checkNotNull(client, "client", new Object[0]);
            this.retryer = Util.checkNotNull(retryer, "retryer", new Object[0]);
            this.requestInterceptors = Util.checkNotNull(requestInterceptors, "requestInterceptors", new Object[0]);
            this.logger = Util.checkNotNull(logger, "logger", new Object[0]);
            this.logLevel = Util.checkNotNull(logLevel, "logLevel", new Object[0]);
            this.decode404 = decode404;
            this.closeAfterDecode = closeAfterDecode;
            this.propagationPolicy = propagationPolicy;
        }

        public InvocationHandlerFactory.MethodHandler create(Target<?> target, MethodMetadata md, RequestTemplate.Factory buildTemplateFromArgs, Request.Options options, Decoder decoder, ErrorDecoder errorDecoder) {
            return new SynchronousMethodHandler(target, this.client, this.retryer, this.requestInterceptors, this.logger, this.logLevel, md, buildTemplateFromArgs, options, decoder, errorDecoder, this.decode404, this.closeAfterDecode, this.propagationPolicy);
        }
    }
}

