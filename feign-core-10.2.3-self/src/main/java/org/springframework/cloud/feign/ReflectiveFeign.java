/*
 * Decompiled with CFR 0.152.
 */
package feign;

import feign.Contract;
import feign.DefaultMethodHandler;
import feign.Feign;
import feign.InvocationHandlerFactory;
import feign.MethodMetadata;
import feign.Param;
import feign.QueryMapEncoder;
import feign.Request;
import feign.RequestTemplate;
import feign.SynchronousMethodHandler;
import feign.Target;
import feign.Util;
import feign.codec.Decoder;
import feign.codec.EncodeException;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;
import feign.template.UriUtils;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class ReflectiveFeign
extends Feign {
    private final ParseHandlersByName targetToHandlersByName;
    private final InvocationHandlerFactory factory;
    private final QueryMapEncoder queryMapEncoder;

    ReflectiveFeign(ParseHandlersByName targetToHandlersByName, InvocationHandlerFactory factory, QueryMapEncoder queryMapEncoder) {
        this.targetToHandlersByName = targetToHandlersByName;
        this.factory = factory;
        this.queryMapEncoder = queryMapEncoder;
    }

    @Override
    public <T> T newInstance(Target<T> target) {
        Map<String, InvocationHandlerFactory.MethodHandler> nameToHandler = this.targetToHandlersByName.apply(target);
        LinkedHashMap<Method, InvocationHandlerFactory.MethodHandler> methodToHandler = new LinkedHashMap<Method, InvocationHandlerFactory.MethodHandler>();
        LinkedList<DefaultMethodHandler> defaultMethodHandlers = new LinkedList<DefaultMethodHandler>();
        for (Method method : target.type().getMethods()) {
            if (method.getDeclaringClass() == Object.class) continue;
            if (Util.isDefault(method)) {
                DefaultMethodHandler handler = new DefaultMethodHandler(method);
                defaultMethodHandlers.add(handler);
                methodToHandler.put(method, handler);
                continue;
            }
            methodToHandler.put(method, nameToHandler.get(Feign.configKey(target.type(), method)));
        }
        InvocationHandler handler = this.factory.create(target, methodToHandler);
        Object proxy = Proxy.newProxyInstance(target.type().getClassLoader(), new Class[]{target.type()}, handler);
        for (DefaultMethodHandler defaultMethodHandler : defaultMethodHandlers) {
            defaultMethodHandler.bindTo(proxy);
        }
        return (T)proxy;
    }

    private static class BuildEncodedTemplateFromArgs
    extends BuildTemplateByResolvingArgs {
        private final Encoder encoder;

        private BuildEncodedTemplateFromArgs(MethodMetadata metadata, Encoder encoder, QueryMapEncoder queryMapEncoder) {
            super(metadata, queryMapEncoder);
            this.encoder = encoder;
        }

        @Override
        protected RequestTemplate resolve(Object[] argv, RequestTemplate mutable, Map<String, Object> variables) {
            Object body = argv[this.metadata.bodyIndex()];
            Util.checkArgument(body != null, "Body parameter %s was null", this.metadata.bodyIndex());
            try {
                this.encoder.encode(body, this.metadata.bodyType(), mutable);
            }
            catch (EncodeException e) {
                throw e;
            }
            catch (RuntimeException e) {
                throw new EncodeException(e.getMessage(), e);
            }
            return super.resolve(argv, mutable, variables);
        }
    }

    private static class BuildFormEncodedTemplateFromArgs
    extends BuildTemplateByResolvingArgs {
        private final Encoder encoder;

        private BuildFormEncodedTemplateFromArgs(MethodMetadata metadata, Encoder encoder, QueryMapEncoder queryMapEncoder) {
            super(metadata, queryMapEncoder);
            this.encoder = encoder;
        }

        @Override
        protected RequestTemplate resolve(Object[] argv, RequestTemplate mutable, Map<String, Object> variables) {
            LinkedHashMap<String, Object> formVariables = new LinkedHashMap<String, Object>();
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                if (!this.metadata.formParams().contains(entry.getKey())) continue;
                formVariables.put(entry.getKey(), entry.getValue());
            }
            try {
                this.encoder.encode(formVariables, Encoder.MAP_STRING_WILDCARD, mutable);
            }
            catch (EncodeException e) {
                throw e;
            }
            catch (RuntimeException e) {
                throw new EncodeException(e.getMessage(), e);
            }
            return super.resolve(argv, mutable, variables);
        }
    }

    private static class BuildTemplateByResolvingArgs
    implements RequestTemplate.Factory {
        private final QueryMapEncoder queryMapEncoder;
        protected final MethodMetadata metadata;
        private final Map<Integer, Param.Expander> indexToExpander = new LinkedHashMap<Integer, Param.Expander>();

        private BuildTemplateByResolvingArgs(MethodMetadata metadata, QueryMapEncoder queryMapEncoder) {
            this.metadata = metadata;
            this.queryMapEncoder = queryMapEncoder;
            if (metadata.indexToExpander() != null) {
                this.indexToExpander.putAll(metadata.indexToExpander());
                return;
            }
            if (metadata.indexToExpanderClass().isEmpty()) {
                return;
            }
            for (Map.Entry<Integer, Class<? extends Param.Expander>> indexToExpanderClass : metadata.indexToExpanderClass().entrySet()) {
                try {
                    this.indexToExpander.put(indexToExpanderClass.getKey(), indexToExpanderClass.getValue().newInstance());
                }
                catch (InstantiationException e) {
                    throw new IllegalStateException(e);
                }
                catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
        }

        @Override
        public RequestTemplate create(Object[] argv) {
            RequestTemplate mutable = RequestTemplate.from(this.metadata.template());
            if (this.metadata.urlIndex() != null) {
                int urlIndex = this.metadata.urlIndex();
                Util.checkArgument(argv[urlIndex] != null, "URI parameter %s was null", urlIndex);
                mutable.target(String.valueOf(argv[urlIndex]));
            }
            LinkedHashMap<String, Object> varBuilder = new LinkedHashMap<String, Object>();
            for (Map.Entry<Integer, Collection<String>> entry : this.metadata.indexToName().entrySet()) {
                int i = entry.getKey();
                Object value = argv[entry.getKey()];
                if (value == null) continue;
                if (this.indexToExpander.containsKey(i)) {
                    value = this.expandElements(this.indexToExpander.get(i), value);
                }
                for (String name : entry.getValue()) {
                    varBuilder.put(name, value);
                }
            }
            RequestTemplate template = this.resolve(argv, mutable, varBuilder);
            if (this.metadata.queryMapIndex() != null) {
                Object value = argv[this.metadata.queryMapIndex()];
                Map<String, Object> queryMap = this.toQueryMap(value);
                template = this.addQueryMapQueryParameters(queryMap, template);
            }
            if (this.metadata.headerMapIndex() != null) {
                template = this.addHeaderMapHeaders((Map)argv[this.metadata.headerMapIndex()], template);
            }
            return template;
        }

        private Map<String, Object> toQueryMap(Object value) {
            if (value instanceof Map) {
                return (Map)value;
            }
            try {
                return this.queryMapEncoder.encode(value);
            }
            catch (EncodeException e) {
                throw new IllegalStateException(e);
            }
        }

        private Object expandElements(Param.Expander expander, Object value) {
            if (value instanceof Iterable) {
                return this.expandIterable(expander, (Iterable)value);
            }
            return expander.expand(value);
        }

        private List<String> expandIterable(Param.Expander expander, Iterable value) {
            ArrayList<String> values = new ArrayList<String>();
            for (Object element : value) {
                if (element == null) continue;
                values.add(expander.expand(element));
            }
            return values;
        }

        private RequestTemplate addHeaderMapHeaders(Map<String, Object> headerMap, RequestTemplate mutable) {
            for (Map.Entry<String, Object> currEntry : headerMap.entrySet()) {
                ArrayList<String> values = new ArrayList<String>();
                Object currValue = currEntry.getValue();
                if (currValue instanceof Iterable) {
                    for (Object nextObject : (Iterable)currValue) {
                        values.add(nextObject == null ? null : nextObject.toString());
                    }
                } else {
                    values.add(currValue == null ? null : currValue.toString());
                }
                mutable.header(currEntry.getKey(), values);
            }
            return mutable;
        }

        private RequestTemplate addQueryMapQueryParameters(Map<String, Object> queryMap, RequestTemplate mutable) {
            for (Map.Entry<String, Object> currEntry : queryMap.entrySet()) {
                ArrayList<String> values = new ArrayList<String>();
                boolean encoded = this.metadata.queryMapEncoded();
                Object currValue = currEntry.getValue();
                if (currValue instanceof Iterable) {
                    for (Object nextObject : (Iterable)currValue) {
                        values.add(nextObject == null ? null : (encoded ? nextObject.toString() : UriUtils.encode(nextObject.toString())));
                    }
                } else {
                    values.add(currValue == null ? null : (encoded ? currValue.toString() : UriUtils.encode(currValue.toString())));
                }
                mutable.query(encoded ? currEntry.getKey() : UriUtils.encode(currEntry.getKey()), values);
            }
            return mutable;
        }

        protected RequestTemplate resolve(Object[] argv, RequestTemplate mutable, Map<String, Object> variables) {
            return mutable.resolve(variables);
        }
    }

    static final class ParseHandlersByName {
        private final Contract contract;
        private final Request.Options options;
        private final Encoder encoder;
        private final Decoder decoder;
        private final ErrorDecoder errorDecoder;
        private final QueryMapEncoder queryMapEncoder;
        private final SynchronousMethodHandler.Factory factory;

        ParseHandlersByName(Contract contract, Request.Options options, Encoder encoder, Decoder decoder, QueryMapEncoder queryMapEncoder, ErrorDecoder errorDecoder, SynchronousMethodHandler.Factory factory) {
            this.contract = contract;
            this.options = options;
            this.factory = factory;
            this.errorDecoder = errorDecoder;
            this.queryMapEncoder = queryMapEncoder;
            this.encoder = Util.checkNotNull(encoder, "encoder", new Object[0]);
            this.decoder = Util.checkNotNull(decoder, "decoder", new Object[0]);
        }

        public Map<String, InvocationHandlerFactory.MethodHandler> apply(Target key) {
            List<MethodMetadata> metadata = this.contract.parseAndValidatateMetadata(key.type());
            LinkedHashMap<String, InvocationHandlerFactory.MethodHandler> result = new LinkedHashMap<String, InvocationHandlerFactory.MethodHandler>();
            for (MethodMetadata md : metadata) {
                BuildTemplateByResolvingArgs buildTemplate = !md.formParams().isEmpty() && md.template().bodyTemplate() == null ? new BuildFormEncodedTemplateFromArgs(md, this.encoder, this.queryMapEncoder) : (md.bodyIndex() != null ? new BuildEncodedTemplateFromArgs(md, this.encoder, this.queryMapEncoder) : new BuildTemplateByResolvingArgs(md, this.queryMapEncoder));
                result.put(md.configKey(), this.factory.create(key, md, buildTemplate, this.options, this.decoder, this.errorDecoder));
            }
            return result;
        }
    }

    static class FeignInvocationHandler
    implements InvocationHandler {
        private final Target target;
        private final Map<Method, InvocationHandlerFactory.MethodHandler> dispatch;

        FeignInvocationHandler(Target target, Map<Method, InvocationHandlerFactory.MethodHandler> dispatch) {
            this.target = Util.checkNotNull(target, "target", new Object[0]);
            this.dispatch = Util.checkNotNull(dispatch, "dispatch for %s", target);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if ("equals".equals(method.getName())) {
                try {
                    InvocationHandler otherHandler = args.length > 0 && args[0] != null ? Proxy.getInvocationHandler(args[0]) : null;
                    return this.equals(otherHandler);
                }
                catch (IllegalArgumentException e) {
                    return false;
                }
            }
            if ("hashCode".equals(method.getName())) {
                return this.hashCode();
            }
            if ("toString".equals(method.getName())) {
                return this.toString();
            }
            return this.dispatch.get(method).invoke(args);
        }

        public boolean equals(Object obj) {
            if (obj instanceof FeignInvocationHandler) {
                FeignInvocationHandler other = (FeignInvocationHandler)obj;
                return this.target.equals(other.target);
            }
            return false;
        }

        public int hashCode() {
            return this.target.hashCode();
        }

        public String toString() {
            return this.target.toString();
        }
    }
}

