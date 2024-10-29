/*
 * Decompiled with CFR 0.152.
 */
package feign;

import feign.Experimental;
import feign.FeignException;
import feign.Request;
import feign.Util;

@Experimental
public class AsyncJoinException
extends FeignException {
    private static final long serialVersionUID = 1L;

    public AsyncJoinException(int status, String message, Request request, Throwable cause) {
        super(status, message, request, Util.checkNotNull(cause, "cause", new Object[0]));
    }
}

