/*
 * Decompiled with CFR 0.152.
 */
package feign.codec;

import feign.FeignException;
import feign.Request;
import feign.Util;

public class DecodeException
extends FeignException {
    private static final long serialVersionUID = 1L;

    public DecodeException(int status, String message, Request request) {
        super(status, Util.checkNotNull(message, "message", new Object[0]), request);
    }

    public DecodeException(int status, String message, Request request, Throwable cause) {
        super(status, message, request, Util.checkNotNull(cause, "cause", new Object[0]));
    }
}

