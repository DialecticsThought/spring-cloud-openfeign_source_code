/*
 * Decompiled with CFR 0.152.
 */
package feign;

import feign.FeignException;
import feign.Response;
import feign.codec.DecodeException;
import feign.codec.Decoder;
import java.io.IOException;
import java.lang.reflect.Type;

public class InvocationContext {
    private final Decoder decoder;
    private final Type returnType;
    private final Response response;

    InvocationContext(Decoder decoder, Type returnType, Response response) {
        this.decoder = decoder;
        this.returnType = returnType;
        this.response = response;
    }

    public Object proceed() {
        try {
            return this.decoder.decode(this.response, this.returnType);
        }
        catch (FeignException e) {
            throw e;
        }
        catch (RuntimeException e) {
            throw new DecodeException(this.response.status(), e.getMessage(), this.response.request(), e);
        }
        catch (IOException e) {
            throw FeignException.errorReading(this.response.request(), this.response, e);
        }
    }

    public Decoder decoder() {
        return this.decoder;
    }

    public Type returnType() {
        return this.returnType;
    }

    public Response response() {
        return this.response;
    }
}

