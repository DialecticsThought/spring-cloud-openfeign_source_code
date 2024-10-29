/*
 * Decompiled with CFR 0.152.
 */
package org.springframework.cloud.feign.codec;

import feign.FeignException;
import feign.Util;

public class DecodeException
extends FeignException {
    private static final long serialVersionUID = 1L;

    public DecodeException(int status, String message) {
        super(status, Util.checkNotNull(message, "message", new Object[0]));
    }

    public DecodeException(int status, String message, Throwable cause) {
        super(status, message, Util.checkNotNull(cause, "cause", new Object[0]));
    }
}

