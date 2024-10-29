/*
 * Decompiled with CFR 0.152.
 */
package feign;

import feign.Capability;
import feign.Contract;
import feign.ExceptionPropagationPolicy;
import feign.Feign;
import feign.InvocationHandlerFactory;
import feign.Logger;
import feign.QueryMapEncoder;
import feign.Request;
import feign.RequestInterceptor;
import feign.ResponseInterceptor;
import feign.ResponseMapper;
import feign.Retryer;
import feign.Util;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;
import feign.querymap.FieldQueryMapEncoder;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public abstract class BaseBuilder<B extends BaseBuilder<B>> {
    private final B thisB;
    protected final List<RequestInterceptor> requestInterceptors = new ArrayList<RequestInterceptor>();
    protected ResponseInterceptor responseInterceptor = ResponseInterceptor.DEFAULT;
    protected Logger.Level logLevel = Logger.Level.NONE;
    protected Contract contract = new Contract.Default();
    protected Retryer retryer = new Retryer.Default();
    protected Logger logger = new Logger.NoOpLogger();
    protected Encoder encoder = new Encoder.Default();
    protected Decoder decoder = new Decoder.Default();
    protected boolean closeAfterDecode = true;
    protected QueryMapEncoder queryMapEncoder = new FieldQueryMapEncoder();
    protected ErrorDecoder errorDecoder = new ErrorDecoder.Default();
    protected Request.Options options = new Request.Options();
    protected InvocationHandlerFactory invocationHandlerFactory = new InvocationHandlerFactory.Default();
    protected boolean dismiss404;
    protected ExceptionPropagationPolicy propagationPolicy = ExceptionPropagationPolicy.NONE;
    protected List<Capability> capabilities = new ArrayList<Capability>();

    public BaseBuilder() {
        this.thisB = this;
    }

    public B logLevel(Logger.Level logLevel) {
        this.logLevel = logLevel;
        return this.thisB;
    }

    public B contract(Contract contract) {
        this.contract = contract;
        return this.thisB;
    }

    public B retryer(Retryer retryer) {
        this.retryer = retryer;
        return this.thisB;
    }

    public B logger(Logger logger) {
        this.logger = logger;
        return this.thisB;
    }

    public B encoder(Encoder encoder) {
        this.encoder = encoder;
        return this.thisB;
    }

    public B decoder(Decoder decoder) {
        this.decoder = decoder;
        return this.thisB;
    }

    public B doNotCloseAfterDecode() {
        this.closeAfterDecode = false;
        return this.thisB;
    }

    public B queryMapEncoder(QueryMapEncoder queryMapEncoder) {
        this.queryMapEncoder = queryMapEncoder;
        return this.thisB;
    }

    public B mapAndDecode(ResponseMapper mapper, Decoder decoder) {
        this.decoder = new Feign.ResponseMappingDecoder(mapper, decoder);
        return this.thisB;
    }

    public B dismiss404() {
        this.dismiss404 = true;
        return this.thisB;
    }

    @Deprecated
    public B decode404() {
        this.dismiss404 = true;
        return this.thisB;
    }

    public B errorDecoder(ErrorDecoder errorDecoder) {
        this.errorDecoder = errorDecoder;
        return this.thisB;
    }

    public B options(Request.Options options) {
        this.options = options;
        return this.thisB;
    }

    public B requestInterceptor(RequestInterceptor requestInterceptor) {
        this.requestInterceptors.add(requestInterceptor);
        return this.thisB;
    }

    public B requestInterceptors(Iterable<RequestInterceptor> requestInterceptors) {
        this.requestInterceptors.clear();
        for (RequestInterceptor requestInterceptor : requestInterceptors) {
            this.requestInterceptors.add(requestInterceptor);
        }
        return this.thisB;
    }

    public B responseInterceptor(ResponseInterceptor responseInterceptor) {
        this.responseInterceptor = responseInterceptor;
        return this.thisB;
    }

    public B invocationHandlerFactory(InvocationHandlerFactory invocationHandlerFactory) {
        this.invocationHandlerFactory = invocationHandlerFactory;
        return this.thisB;
    }

    public B exceptionPropagationPolicy(ExceptionPropagationPolicy propagationPolicy) {
        this.propagationPolicy = propagationPolicy;
        return this.thisB;
    }

    public B addCapability(Capability capability) {
        this.capabilities.add(capability);
        return this.thisB;
    }

    protected B enrich() {
        if (this.capabilities.isEmpty()) {
            return this.thisB;
        }
        this.getFieldsToEnrich().forEach(field -> {
            field.setAccessible(true);
            try {
                List enriched;
                Object originalValue = field.get(this.thisB);
                if (originalValue instanceof List) {
                    Type ownerType = ((ParameterizedType)field.getGenericType()).getActualTypeArguments()[0];
                    enriched = ((List)originalValue).stream().map(value -> Capability.enrich(value, (Class)ownerType, this.capabilities)).collect(Collectors.toList());
                } else {
                    enriched = Capability.enrich(originalValue, field.getType(), this.capabilities);
                }
                field.set(this.thisB, enriched);
            }
            catch (IllegalAccessException | IllegalArgumentException e) {
                throw new RuntimeException("Unable to enrich field " + field, e);
            }
            finally {
                field.setAccessible(false);
            }
        });
        return this.thisB;
    }

    List<Field> getFieldsToEnrich() {
        return Util.allFields(this.getClass()).stream().filter(field -> !field.isSynthetic()).filter(field -> !Objects.equals(field.getName(), "capabilities")).filter(field -> !Objects.equals(field.getName(), "thisB")).filter(field -> !field.getType().isPrimitive()).filter(field -> !field.getType().isEnum()).collect(Collectors.toList());
    }
}

