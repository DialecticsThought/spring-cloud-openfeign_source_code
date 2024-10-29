/*
 * Decompiled with CFR 0.152.
 */
package feign;

import feign.FeignException;
import feign.Request;
import java.util.Date;

public class RetryableException
extends FeignException {
    private static final long serialVersionUID = 1L;
    private final Long retryAfter;
    private final Request.HttpMethod httpMethod;

    public RetryableException(int status, String message, Request.HttpMethod httpMethod, Throwable cause, Date retryAfter, Request request) {
        super(status, message, request, cause);
        this.httpMethod = httpMethod;
        this.retryAfter = retryAfter != null ? Long.valueOf(retryAfter.getTime()) : null;
    }

    public RetryableException(int status, String message, Request.HttpMethod httpMethod, Date retryAfter, Request request) {
        super(status, message, request);
        this.httpMethod = httpMethod;
        this.retryAfter = retryAfter != null ? Long.valueOf(retryAfter.getTime()) : null;
    }

    public Date retryAfter() {
        return this.retryAfter != null ? new Date(this.retryAfter) : null;
    }

    public Request.HttpMethod method() {
        return this.httpMethod;
    }
}

