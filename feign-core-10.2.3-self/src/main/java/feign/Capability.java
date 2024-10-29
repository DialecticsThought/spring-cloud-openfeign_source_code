/*
 * Decompiled with CFR 0.152.
 */
package feign;

import feign.AsyncClient;
import feign.AsyncContextSupplier;
import feign.AsyncResponseHandler;
import feign.Client;
import feign.Contract;
import feign.InvocationHandlerFactory;
import feign.Logger;
import feign.QueryMapEncoder;
import feign.Request;
import feign.RequestInterceptor;
import feign.ResponseInterceptor;
import feign.Retryer;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;

public interface Capability {
    public static Object enrich(Object componentToEnrich, Class<?> capabilityToEnrich, List<Capability> capabilities) {
        return capabilities.stream().reduce(componentToEnrich, (target, capability) -> Capability.invoke(target, capability, capabilityToEnrich), (component, enrichedComponent) -> enrichedComponent);
    }

    public static Object invoke(Object target, Capability capability, Class<?> capabilityToEnrich) {
        return Arrays.stream(capability.getClass().getMethods()).filter(method -> method.getName().equals("enrich")).filter(method -> method.getReturnType().isAssignableFrom(capabilityToEnrich)).findFirst().map(method -> {
            try {
                return method.invoke((Object)capability, target);
            }
            catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                throw new RuntimeException("Unable to enrich " + target, e);
            }
        }).orElse(target);
    }

    default public Client enrich(Client client) {
        return client;
    }

    default public AsyncClient<Object> enrich(AsyncClient<Object> client) {
        return client;
    }

    default public Retryer enrich(Retryer retryer) {
        return retryer;
    }

    default public RequestInterceptor enrich(RequestInterceptor requestInterceptor) {
        return requestInterceptor;
    }

    default public ResponseInterceptor enrich(ResponseInterceptor responseInterceptor) {
        return responseInterceptor;
    }

    default public Logger enrich(Logger logger) {
        return logger;
    }

    default public Logger.Level enrich(Logger.Level level) {
        return level;
    }

    default public Contract enrich(Contract contract) {
        return contract;
    }

    default public Request.Options enrich(Request.Options options) {
        return options;
    }

    default public Encoder enrich(Encoder encoder) {
        return encoder;
    }

    default public Decoder enrich(Decoder decoder) {
        return decoder;
    }

    default public ErrorDecoder enrich(ErrorDecoder decoder) {
        return decoder;
    }

    default public InvocationHandlerFactory enrich(InvocationHandlerFactory invocationHandlerFactory) {
        return invocationHandlerFactory;
    }

    default public QueryMapEncoder enrich(QueryMapEncoder queryMapEncoder) {
        return queryMapEncoder;
    }

    default public AsyncResponseHandler enrich(AsyncResponseHandler asyncResponseHandler) {
        return asyncResponseHandler;
    }

    default public <C> AsyncContextSupplier<C> enrich(AsyncContextSupplier<C> asyncContextSupplier) {
        return asyncContextSupplier;
    }
}

