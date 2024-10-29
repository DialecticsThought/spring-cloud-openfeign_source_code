/*
 * Decompiled with CFR 0.152.
 */
package feign;

import feign.AlwaysEncodeBodyContract;
import feign.Body;
import feign.DeclarativeContract;
import feign.Feign;
import feign.HeaderMap;
import feign.Headers;
import feign.MethodMetadata;
import feign.Param;
import feign.QueryMap;
import feign.Request;
import feign.RequestLine;
import feign.Types;
import feign.Util;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public interface Contract {
    public List<MethodMetadata> parseAndValidateMetadata(Class<?> var1);

    public static class Default
    extends DeclarativeContract {
        static final Pattern REQUEST_LINE_PATTERN = Pattern.compile("^([A-Z]+)[ ]*(.*)$");

        public Default() {
            super.registerClassAnnotation(Headers.class, (E header, MethodMetadata data) -> {
                String[] headersOnType = header.value();
                Util.checkState(headersOnType.length > 0, "Headers annotation was empty on type %s.", data.configKey());
                Map<String, Collection<String>> headers = Default.toMap(headersOnType);
                headers.putAll(data.template().headers());
                data.template().headers(null);
                data.template().headers(headers);
            });
            super.registerMethodAnnotation(RequestLine.class, (E ann, MethodMetadata data) -> {
                String requestLine = ann.value();
                Util.checkState(Util.emptyToNull(requestLine) != null, "RequestLine annotation was empty on method %s.", data.configKey());
                Matcher requestLineMatcher = REQUEST_LINE_PATTERN.matcher(requestLine);
                if (!requestLineMatcher.find()) {
                    throw new IllegalStateException(String.format("RequestLine annotation didn't start with an HTTP verb on method %s", data.configKey()));
                }
                data.template().method(Request.HttpMethod.valueOf(requestLineMatcher.group(1)));
                data.template().uri(requestLineMatcher.group(2));
                data.template().decodeSlash(ann.decodeSlash());
                data.template().collectionFormat(ann.collectionFormat());
            });
            super.registerMethodAnnotation(Body.class, (E ann, MethodMetadata data) -> {
                String body = ann.value();
                Util.checkState(Util.emptyToNull(body) != null, "Body annotation was empty on method %s.", data.configKey());
                if (body.indexOf(123) == -1) {
                    data.template().body(body);
                } else {
                    data.template().bodyTemplate(body);
                }
            });
            super.registerMethodAnnotation(Headers.class, (E header, MethodMetadata data) -> {
                String[] headersOnMethod = header.value();
                Util.checkState(headersOnMethod.length > 0, "Headers annotation was empty on method %s.", data.configKey());
                data.template().headers(Default.toMap(headersOnMethod));
            });
            super.registerParameterAnnotation(Param.class, (paramAnnotation, data, paramIndex) -> {
                String annotationName = paramAnnotation.value();
                Parameter parameter = data.method().getParameters()[paramIndex];
                String name = Util.emptyToNull(annotationName) == null && parameter.isNamePresent() ? parameter.getName() : annotationName;
                Util.checkState(Util.emptyToNull(name) != null, "Param annotation was empty on param %s.", paramIndex);
                this.nameParam(data, name, paramIndex);
                Class<? extends Param.Expander> expander = paramAnnotation.expander();
                if (expander != Param.ToStringExpander.class) {
                    data.indexToExpanderClass().put(paramIndex, expander);
                }
                if (!data.template().hasRequestVariable(name)) {
                    data.formParams().add(name);
                }
            });
            super.registerParameterAnnotation(QueryMap.class, (queryMap, data, paramIndex) -> {
                Util.checkState(data.queryMapIndex() == null, "QueryMap annotation was present on multiple parameters.", new Object[0]);
                data.queryMapIndex(paramIndex);
            });
            super.registerParameterAnnotation(HeaderMap.class, (queryMap, data, paramIndex) -> {
                Util.checkState(data.headerMapIndex() == null, "HeaderMap annotation was present on multiple parameters.", new Object[0]);
                data.headerMapIndex(paramIndex);
            });
        }

        private static Map<String, Collection<String>> toMap(String[] input) {
            LinkedHashMap<String, Collection<String>> result = new LinkedHashMap<String, Collection<String>>(input.length);
            for (String header : input) {
                int colon = header.indexOf(58);
                String name = header.substring(0, colon);
                if (!result.containsKey(name)) {
                    result.put(name, new ArrayList(1));
                }
                ((Collection)result.get(name)).add(header.substring(colon + 1).trim());
            }
            return result;
        }
    }

    public static abstract class BaseContract
    implements Contract {
        @Override
        public List<MethodMetadata> parseAndValidateMetadata(Class<?> targetType) {
            Util.checkState(targetType.getTypeParameters().length == 0, "Parameterized types unsupported: %s", targetType.getSimpleName());
            Util.checkState(targetType.getInterfaces().length <= 1, "Only single inheritance supported: %s", targetType.getSimpleName());
            LinkedHashMap<String, MethodMetadata> result = new LinkedHashMap<String, MethodMetadata>();
            for (Method method : targetType.getMethods()) {
                if (method.getDeclaringClass() == Object.class || (method.getModifiers() & 8) != 0 || Util.isDefault(method)) continue;
                MethodMetadata metadata = this.parseAndValidateMetadata(targetType, method);
                if (result.containsKey(metadata.configKey())) {
                    Type overridingReturnType;
                    MethodMetadata existingMetadata = (MethodMetadata)result.get(metadata.configKey());
                    Type existingReturnType = existingMetadata.returnType();
                    Type resolvedType = Types.resolveReturnType(existingReturnType, overridingReturnType = metadata.returnType());
                    if (!resolvedType.equals(overridingReturnType)) continue;
                    result.put(metadata.configKey(), metadata);
                    continue;
                }
                result.put(metadata.configKey(), metadata);
            }
            return new ArrayList<MethodMetadata>(result.values());
        }

        @Deprecated
        public MethodMetadata parseAndValidateMetadata(Method method) {
            return this.parseAndValidateMetadata(method.getDeclaringClass(), method);
        }

        protected MethodMetadata parseAndValidateMetadata(Class<?> targetType, Method method) {
            MethodMetadata data = new MethodMetadata();
            data.targetType(targetType);
            data.method(method);
            data.returnType(Types.resolve(targetType, targetType, method.getGenericReturnType()));
            data.configKey(Feign.configKey(targetType, method));
            if (AlwaysEncodeBodyContract.class.isAssignableFrom(this.getClass())) {
                data.alwaysEncodeBody(true);
            }
            if (targetType.getInterfaces().length == 1) {
                this.processAnnotationOnClass(data, targetType.getInterfaces()[0]);
            }
            this.processAnnotationOnClass(data, targetType);
            for (Annotation methodAnnotation : method.getAnnotations()) {
                this.processAnnotationOnMethod(data, methodAnnotation, method);
            }
            if (data.isIgnored()) {
                return data;
            }
            Util.checkState(data.template().method() != null, "Method %s not annotated with HTTP method type (ex. GET, POST)%s", data.configKey(), data.warnings());
            Class<?>[] parameterTypes = method.getParameterTypes();
            Type[] genericParameterTypes = method.getGenericParameterTypes();
            Annotation[][] parameterAnnotations = method.getParameterAnnotations();
            int count = parameterAnnotations.length;
            for (int i = 0; i < count; ++i) {
                boolean isHttpAnnotation = false;
                if (parameterAnnotations[i] != null) {
                    isHttpAnnotation = this.processAnnotationsOnParameter(data, parameterAnnotations[i], i);
                }
                if (isHttpAnnotation) {
                    data.ignoreParamater(i);
                }
                if (parameterTypes[i] == URI.class) {
                    data.urlIndex(i);
                    continue;
                }
                if (isHttpAnnotation || Request.Options.class.isAssignableFrom(parameterTypes[i])) continue;
                if (data.isAlreadyProcessed(i)) {
                    Util.checkState(data.formParams().isEmpty() || data.bodyIndex() == null, "Body parameters cannot be used with form parameters.%s", data.warnings());
                    continue;
                }
                if (data.alwaysEncodeBody()) continue;
                Util.checkState(data.formParams().isEmpty(), "Body parameters cannot be used with form parameters.%s", data.warnings());
                Util.checkState(data.bodyIndex() == null, "Method has too many Body parameters: %s%s", method, data.warnings());
                data.bodyIndex(i);
                data.bodyType(Types.resolve(targetType, targetType, genericParameterTypes[i]));
            }
            if (data.headerMapIndex() != null && Map.class.isAssignableFrom(parameterTypes[data.headerMapIndex()])) {
                BaseContract.checkMapKeys("HeaderMap", genericParameterTypes[data.headerMapIndex()]);
            }
            if (data.queryMapIndex() != null && Map.class.isAssignableFrom(parameterTypes[data.queryMapIndex()])) {
                BaseContract.checkMapKeys("QueryMap", genericParameterTypes[data.queryMapIndex()]);
            }
            return data;
        }

        private static void checkMapString(String name, Class<?> type, Type genericType) {
            Util.checkState(Map.class.isAssignableFrom(type), "%s parameter must be a Map: %s", name, type);
            BaseContract.checkMapKeys(name, genericType);
        }

        private static void checkMapKeys(String name, Type genericType) {
            Class keyClass = null;
            if (ParameterizedType.class.isAssignableFrom(genericType.getClass())) {
                Type[] parameterTypes = ((ParameterizedType)genericType).getActualTypeArguments();
                keyClass = (Class)parameterTypes[0];
            } else if (genericType instanceof Class) {
                Type[] interfaces;
                for (Type extended : interfaces = ((Class)genericType).getGenericInterfaces()) {
                    if (!ParameterizedType.class.isAssignableFrom(extended.getClass())) continue;
                    Type[] parameterTypes = ((ParameterizedType)extended).getActualTypeArguments();
                    keyClass = (Class)parameterTypes[0];
                    break;
                }
            }
            if (keyClass != null) {
                Util.checkState(String.class.equals(keyClass), "%s key must be a String: %s", name, keyClass.getSimpleName());
            }
        }

        protected abstract void processAnnotationOnClass(MethodMetadata var1, Class<?> var2);

        protected abstract void processAnnotationOnMethod(MethodMetadata var1, Annotation var2, Method var3);

        protected abstract boolean processAnnotationsOnParameter(MethodMetadata var1, Annotation[] var2, int var3);

        protected void nameParam(MethodMetadata data, String name, int i) {
            ArrayList<String> names = data.indexToName().containsKey(i) ? data.indexToName().get(i) : new ArrayList<String>();
            names.add(name);
            data.indexToName().put(i, names);
        }
    }
}

