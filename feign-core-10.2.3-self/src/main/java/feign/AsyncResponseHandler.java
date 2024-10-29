/*
 * Decompiled with CFR 0.152.
 */
package feign;

import feign.Experimental;
import feign.FeignException;
import feign.InvocationContext;
import feign.Logger;
import feign.Response;
import feign.ResponseInterceptor;
import feign.Util;
import feign.codec.Decoder;
import feign.codec.ErrorDecoder;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.concurrent.CompletableFuture;

@Experimental
class AsyncResponseHandler {
    private static final long MAX_RESPONSE_BUFFER_SIZE = 8192L;
    private final Logger.Level logLevel;
    private final Logger logger;
    private final Decoder decoder;
    private final ErrorDecoder errorDecoder;
    private final boolean dismiss404;
    private final boolean closeAfterDecode;
    private final ResponseInterceptor responseInterceptor;

    AsyncResponseHandler(Logger.Level logLevel, Logger logger, Decoder decoder, ErrorDecoder errorDecoder, boolean dismiss404, boolean closeAfterDecode, ResponseInterceptor responseInterceptor) {
        this.logLevel = logLevel;
        this.logger = logger;
        this.decoder = decoder;
        this.errorDecoder = errorDecoder;
        this.dismiss404 = dismiss404;
        this.closeAfterDecode = closeAfterDecode;
        this.responseInterceptor = responseInterceptor;
    }

    boolean isVoidType(Type returnType) {
        return Void.class == returnType || Void.TYPE == returnType;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    void handleResponse(CompletableFuture<Object> resultFuture, String configKey, Response response, Type returnType, long elapsedTime) {
        boolean shouldClose = true;
        try {
            if (this.logLevel != Logger.Level.NONE) {
                response = this.logger.logAndRebufferResponse(configKey, this.logLevel, response, elapsedTime);
            }
            if (Response.class == returnType) {
                if (response.body() == null) {
                    resultFuture.complete(response);
                } else if (response.body().length() == null || (long)response.body().length().intValue() > 8192L) {
                    shouldClose = false;
                    resultFuture.complete(response);
                } else {
                    byte[] bodyData = Util.toByteArray(response.body().asInputStream());
                    resultFuture.complete(response.toBuilder().body(bodyData).build());
                }
            } else if (response.status() >= 200 && response.status() < 300) {
                if (this.isVoidType(returnType)) {
                    resultFuture.complete(null);
                } else {
                    Object result = this.decode(response, returnType);
                    shouldClose = this.closeAfterDecode;
                    resultFuture.complete(result);
                }
            } else if (this.dismiss404 && response.status() == 404 && !this.isVoidType(returnType)) {
                Object result = this.decode(response, returnType);
                shouldClose = this.closeAfterDecode;
                resultFuture.complete(result);
            } else {
                resultFuture.completeExceptionally(this.errorDecoder.decode(configKey, response));
            }
        }
        catch (IOException e) {
            if (this.logLevel != Logger.Level.NONE) {
                this.logger.logIOException(configKey, this.logLevel, e, elapsedTime);
            }
            resultFuture.completeExceptionally(FeignException.errorReading(response.request(), response, e));
        }
        catch (Exception e) {
            resultFuture.completeExceptionally(e);
        }
        finally {
            if (shouldClose) {
                Util.ensureClosed(response.body());
            }
        }
    }

    Object decode(Response response, Type type) throws IOException {
        return this.responseInterceptor.aroundDecode(new InvocationContext(this.decoder, type, response));
    }
}

