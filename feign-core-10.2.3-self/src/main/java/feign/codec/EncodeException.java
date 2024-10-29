/*
 * Decompiled with CFR 0.152.
 */
package feign.codec;

import feign.FeignException;
import feign.Util;

public class EncodeException
extends FeignException {
    private static final long serialVersionUID = 1L;

    public EncodeException(String message) {
        super(-1, Util.checkNotNull(message, "message", new Object[0]));
    }

    public EncodeException(String message, Throwable cause) {
        super(-1, message, Util.checkNotNull(cause, "cause", new Object[0]));
    }
}

