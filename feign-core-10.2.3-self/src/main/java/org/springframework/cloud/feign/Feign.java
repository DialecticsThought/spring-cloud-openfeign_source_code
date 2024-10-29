/*
 * Decompiled with CFR 0.152.
 */
package feign;

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
import java.util.ArrayList;
import java.util.List;

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

    static class ResponseMappingDecoder
    implements Decoder {
        private final ResponseMapper mapper;
        private final Decoder delegate;

        ResponseMappingDecoder(ResponseMapper mapper, Decoder decoder) {
            this.mapper = mapper;
            this.delegate = decoder;
        }

        @Override
        public Object decode(Response response, Type type) throws IOException {
            return this.delegate.decode(this.mapper.map(response, type), type);
        }
    }

    public static class Builder {
        private final List<RequestInterceptor> requestInterceptors = new ArrayList<RequestInterceptor>();
        private Logger.Level logLevel = Logger.Level.NONE;
        private Contract contract = new Contract.Default();
        private Client client = new Client.Default(null, null);
        private Retryer retryer = new Retryer.Default();
        private Logger logger = new Logger.NoOpLogger();
        private Encoder encoder = new Encoder.Default();
        private Decoder decoder = new Decoder.Default();
        private QueryMapEncoder queryMapEncoder = new QueryMapEncoder.Default();
        private ErrorDecoder errorDecoder = new ErrorDecoder.Default();
        private Request.Options options = new Request.Options();
        private InvocationHandlerFactory invocationHandlerFactory = new InvocationHandlerFactory.Default();
        private boolean decode404;
        private boolean closeAfterDecode = true;
        private ExceptionPropagationPolicy propagationPolicy = ExceptionPropagationPolicy.NONE;

        public Builder logLevel(Logger.Level logLevel) {
            this.logLevel = logLevel;
            return this;
        }

        public Builder contract(Contract contract) {
            this.contract = contract;
            return this;
        }

        public Builder client(Client client) {
            this.client = client;
            return this;
        }

        public Builder retryer(Retryer retryer) {
            this.retryer = retryer;
            return this;
        }

        public Builder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        public Builder encoder(Encoder encoder) {
            this.encoder = encoder;
            return this;
        }

        public Builder decoder(Decoder decoder) {
            this.decoder = decoder;
            return this;
        }

        public Builder queryMapEncoder(QueryMapEncoder queryMapEncoder) {
            this.queryMapEncoder = queryMapEncoder;
            return this;
        }

        public Builder mapAndDecode(ResponseMapper mapper, Decoder decoder) {
            this.decoder = new ResponseMappingDecoder(mapper, decoder);
            return this;
        }

        public Builder decode404() {
            this.decode404 = true;
            return this;
        }

        public Builder errorDecoder(ErrorDecoder errorDecoder) {
            this.errorDecoder = errorDecoder;
            return this;
        }

        public Builder options(Request.Options options) {
            this.options = options;
            return this;
        }

        public Builder requestInterceptor(RequestInterceptor requestInterceptor) {
            this.requestInterceptors.add(requestInterceptor);
            return this;
        }

        public Builder requestInterceptors(Iterable<RequestInterceptor> requestInterceptors) {
            this.requestInterceptors.clear();
            for (RequestInterceptor requestInterceptor : requestInterceptors) {
                this.requestInterceptors.add(requestInterceptor);
            }
            return this;
        }

        public Builder invocationHandlerFactory(InvocationHandlerFactory invocationHandlerFactory) {
            this.invocationHandlerFactory = invocationHandlerFactory;
            return this;
        }

        public Builder doNotCloseAfterDecode() {
            this.closeAfterDecode = false;
            return this;
        }

        public Builder exceptionPropagationPolicy(ExceptionPropagationPolicy propagationPolicy) {
            this.propagationPolicy = propagationPolicy;
            return this;
        }

        public <T> T target(Class<T> apiType, String url) {
            return this.target(new Target.HardCodedTarget<T>(apiType, url));
        }

        public <T> T target(Target<T> target) {
            return this.build().newInstance(target);
        }

        public Feign build() {
            SynchronousMethodHandler.Factory synchronousMethodHandlerFactory = new SynchronousMethodHandler.Factory(this.client, this.retryer, this.requestInterceptors, this.logger, this.logLevel, this.decode404, this.closeAfterDecode, this.propagationPolicy);
            ReflectiveFeign.ParseHandlersByName handlersByName = new ReflectiveFeign.ParseHandlersByName(this.contract, this.options, this.encoder, this.decoder, this.queryMapEncoder, this.errorDecoder, synchronousMethodHandlerFactory);
            return new ReflectiveFeign(handlersByName, this.invocationHandlerFactory, this.queryMapEncoder);
        }
    }
}

