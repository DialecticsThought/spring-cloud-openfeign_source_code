/*
 * Decompiled with CFR 0.152.
 */
package feign;

import feign.BaseBuilder;
import feign.Capability;
import feign.Client;
import feign.Contract;
import feign.ExceptionPropagationPolicy;
import feign.InvocationHandlerFactory;
import feign.Logger;
import feign.QueryMapEncoder;
import feign.ReflectiveFeign;
import feign.Request;
import feign.RequestInterceptor;
import feign.Response;
import feign.ResponseMapper;
import feign.Retryer;
import feign.SynchronousMethodHandler;
import feign.Target;
import feign.Types;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;

public abstract class Feign {
    public static Builder builder() {
        return new Builder();
    }

    public static String configKey(Class targetType, Method method) {
        StringBuilder builder = new StringBuilder();
        builder.append(targetType.getSimpleName());
        builder.append('#').append(method.getName()).append('(');
        for (Type param : method.getGenericParameterTypes()) {
            param = Types.resolve(targetType, targetType, param);
            builder.append(Types.getRawType(param).getSimpleName()).append(',');
        }
        if (method.getParameterTypes().length > 0) {
            builder.deleteCharAt(builder.length() - 1);
        }
        return builder.append(')').toString();
    }

    @Deprecated
    public static String configKey(Method method) {
        return Feign.configKey(method.getDeclaringClass(), method);
    }

    public abstract <T> T newInstance(Target<T> var1);

    public static class ResponseMappingDecoder
    implements Decoder {
        private final ResponseMapper mapper;
        private final Decoder delegate;

        public ResponseMappingDecoder(ResponseMapper mapper, Decoder decoder) {
            this.mapper = mapper;
            this.delegate = decoder;
        }

        @Override
        public Object decode(Response response, Type type) throws IOException {
            return this.delegate.decode(this.mapper.map(response, type), type);
        }
    }

    public static class Builder
    extends BaseBuilder<Builder> {
        private Client client = new Client.Default(null, null);
        private boolean forceDecoding = false;

        @Override
        public Builder logLevel(Logger.Level logLevel) {
            return (Builder)super.logLevel(logLevel);
        }

        @Override
        public Builder contract(Contract contract) {
            return (Builder)super.contract(contract);
        }

        public Builder client(Client client) {
            this.client = client;
            return this;
        }

        @Override
        public Builder retryer(Retryer retryer) {
            return (Builder)super.retryer(retryer);
        }

        @Override
        public Builder logger(Logger logger) {
            return (Builder)super.logger(logger);
        }

        @Override
        public Builder encoder(Encoder encoder) {
            return (Builder)super.encoder(encoder);
        }

        @Override
        public Builder decoder(Decoder decoder) {
            return (Builder)super.decoder(decoder);
        }

        @Override
        public Builder queryMapEncoder(QueryMapEncoder queryMapEncoder) {
            return (Builder)super.queryMapEncoder(queryMapEncoder);
        }

        @Override
        public Builder mapAndDecode(ResponseMapper mapper, Decoder decoder) {
            return (Builder)super.mapAndDecode(mapper, decoder);
        }

        @Override
        @Deprecated
        public Builder decode404() {
            return (Builder)super.decode404();
        }

        @Override
        public Builder errorDecoder(ErrorDecoder errorDecoder) {
            return (Builder)super.errorDecoder(errorDecoder);
        }

        @Override
        public Builder options(Request.Options options) {
            return (Builder)super.options(options);
        }

        @Override
        public Builder requestInterceptor(RequestInterceptor requestInterceptor) {
            return (Builder)super.requestInterceptor(requestInterceptor);
        }

        @Override
        public Builder requestInterceptors(Iterable<RequestInterceptor> requestInterceptors) {
            return (Builder)super.requestInterceptors(requestInterceptors);
        }

        @Override
        public Builder invocationHandlerFactory(InvocationHandlerFactory invocationHandlerFactory) {
            return (Builder)super.invocationHandlerFactory(invocationHandlerFactory);
        }

        @Override
        public Builder doNotCloseAfterDecode() {
            return (Builder)super.doNotCloseAfterDecode();
        }

        @Override
        public Builder exceptionPropagationPolicy(ExceptionPropagationPolicy propagationPolicy) {
            return (Builder)super.exceptionPropagationPolicy(propagationPolicy);
        }

        @Override
        public Builder addCapability(Capability capability) {
            return (Builder)super.addCapability(capability);
        }

        Builder forceDecoding() {
            this.forceDecoding = true;
            return this;
        }

        public <T> T target(Class<T> apiType, String url) {
            return this.target(new Target.HardCodedTarget<T>(apiType, url));
        }

        public <T> T target(Target<T> target) {
            return this.build().newInstance(target);
        }

        public Feign build() {
            super.enrich();
            SynchronousMethodHandler.Factory synchronousMethodHandlerFactory = new SynchronousMethodHandler.Factory(this.client, this.retryer, this.requestInterceptors, this.responseInterceptor, this.logger, this.logLevel, this.dismiss404, this.closeAfterDecode, this.propagationPolicy, this.forceDecoding);
            ReflectiveFeign.ParseHandlersByName handlersByName = new ReflectiveFeign.ParseHandlersByName(this.contract, this.options, this.encoder, this.decoder, this.queryMapEncoder, this.errorDecoder, synchronousMethodHandlerFactory);
            return new ReflectiveFeign(handlersByName, this.invocationHandlerFactory, this.queryMapEncoder);
        }
    }
}

