/*
 * Decompiled with CFR 0.152.
 */
package feign;

import feign.Body;
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
    public List<MethodMetadata> parseAndValidatateMetadata(Class<?> var1);

    public static class Default
    extends BaseContract {
        static final Pattern REQUEST_LINE_PATTERN = Pattern.compile("^([A-Z]+)[ ]*(.*)$");

        @Override
        protected void processAnnotationOnClass(MethodMetadata data, Class<?> targetType) {
            if (targetType.isAnnotationPresent(Headers.class)) {
                String[] headersOnType = targetType.getAnnotation(Headers.class).value();
                Util.checkState(headersOnType.length > 0, "Headers annotation was empty on type %s.", targetType.getName());
                Map<String, Collection<String>> headers = Default.toMap(headersOnType);
                headers.putAll(data.template().headers());
                data.template().headers(null);
                data.template().headers(headers);
            }
        }

        @Override
        protected void processAnnotationOnMethod(MethodMetadata data, Annotation methodAnnotation, Method method) {
            Class<? extends Annotation> annotationType = methodAnnotation.annotationType();
            if (annotationType == RequestLine.class) {
                String requestLine = ((RequestLine)RequestLine.class.cast(methodAnnotation)).value();
                Util.checkState(Util.emptyToNull(requestLine) != null, "RequestLine annotation was empty on method %s.", method.getName());
                Matcher requestLineMatcher = REQUEST_LINE_PATTERN.matcher(requestLine);
                if (!requestLineMatcher.find()) {
                    throw new IllegalStateException(String.format("RequestLine annotation didn't start with an HTTP verb on method %s", method.getName()));
                }
                data.template().method(Request.HttpMethod.valueOf(requestLineMatcher.group(1)));
                data.template().uri(requestLineMatcher.group(2));
                data.template().decodeSlash(((RequestLine)RequestLine.class.cast(methodAnnotation)).decodeSlash());
                data.template().collectionFormat(((RequestLine)RequestLine.class.cast(methodAnnotation)).collectionFormat());
            } else if (annotationType == Body.class) {
                String body = ((Body)Body.class.cast(methodAnnotation)).value();
                Util.checkState(Util.emptyToNull(body) != null, "Body annotation was empty on method %s.", method.getName());
                if (body.indexOf(123) == -1) {
                    data.template().body(body);
                } else {
                    data.template().bodyTemplate(body);
                }
            } else if (annotationType == Headers.class) {
                String[] headersOnMethod = ((Headers)Headers.class.cast(methodAnnotation)).value();
                Util.checkState(headersOnMethod.length > 0, "Headers annotation was empty on method %s.", method.getName());
                data.template().headers(Default.toMap(headersOnMethod));
            }
        }

        @Override
        protected boolean processAnnotationsOnParameter(MethodMetadata data, Annotation[] annotations, int paramIndex) {
            boolean isHttpAnnotation = false;
            for (Annotation annotation : annotations) {
                Class<? extends Annotation> annotationType = annotation.annotationType();
                if (annotationType == Param.class) {
                    Param paramAnnotation = (Param)annotation;
                    String name = paramAnnotation.value();
                    Util.checkState(Util.emptyToNull(name) != null, "Param annotation was empty on param %s.", paramIndex);
                    this.nameParam(data, name, paramIndex);
                    Class<? extends Param.Expander> expander = paramAnnotation.expander();
                    if (expander != Param.ToStringExpander.class) {
                        data.indexToExpanderClass().put(paramIndex, expander);
                    }
                    data.indexToEncoded().put(paramIndex, paramAnnotation.encoded());
                    isHttpAnnotation = true;
                    if (data.template().hasRequestVariable(name)) continue;
                    data.formParams().add(name);
                    continue;
                }
                if (annotationType == QueryMap.class) {
                    Util.checkState(data.queryMapIndex() == null, "QueryMap annotation was present on multiple parameters.", new Object[0]);
                    data.queryMapIndex(paramIndex);
                    data.queryMapEncoded(((QueryMap)QueryMap.class.cast(annotation)).encoded());
                    isHttpAnnotation = true;
                    continue;
                }
                if (annotationType != HeaderMap.class) continue;
                Util.checkState(data.headerMapIndex() == null, "HeaderMap annotation was present on multiple parameters.", new Object[0]);
                data.headerMapIndex(paramIndex);
                isHttpAnnotation = true;
            }
            return isHttpAnnotation;
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
        public List<MethodMetadata> parseAndValidatateMetadata(Class<?> targetType) {
            Util.checkState(targetType.getTypeParameters().length == 0, "Parameterized types unsupported: %s", targetType.getSimpleName());
            Util.checkState(targetType.getInterfaces().length <= 1, "Only single inheritance supported: %s", targetType.getSimpleName());
            if (targetType.getInterfaces().length == 1) {
                Util.checkState(targetType.getInterfaces()[0].getInterfaces().length == 0, "Only single-level inheritance supported: %s", targetType.getSimpleName());
            }
            LinkedHashMap<String, MethodMetadata> result = new LinkedHashMap<String, MethodMetadata>();
            for (Method method : targetType.getMethods()) {
                if (method.getDeclaringClass() == Object.class || (method.getModifiers() & 8) != 0 || Util.isDefault(method)) continue;
                MethodMetadata metadata = this.parseAndValidateMetadata(targetType, method);
                Util.checkState(!result.containsKey(metadata.configKey()), "Overrides unsupported: %s", metadata.configKey());
                result.put(metadata.configKey(), metadata);
            }
            return new ArrayList<MethodMetadata>(result.values());
        }

        @Deprecated
        public MethodMetadata parseAndValidatateMetadata(Method method) {
            return this.parseAndValidateMetadata(method.getDeclaringClass(), method);
        }

        protected MethodMetadata parseAndValidateMetadata(Class<?> targetType, Method method) {
            MethodMetadata data = new MethodMetadata();
            data.returnType(Types.resolve(targetType, targetType, method.getGenericReturnType()));
            data.configKey(Feign.configKey(targetType, method));
            if (targetType.getInterfaces().length == 1) {
                this.processAnnotationOnClass(data, targetType.getInterfaces()[0]);
            }
            this.processAnnotationOnClass(data, targetType);
            for (Annotation methodAnnotation : method.getAnnotations()) {
                this.processAnnotationOnMethod(data, methodAnnotation, method);
            }
            Util.checkState(data.template().method() != null, "Method %s not annotated with HTTP method type (ex. GET, POST)", method.getName());
            Class<?>[] parameterTypes = method.getParameterTypes();
            Type[] genericParameterTypes = method.getGenericParameterTypes();
            Annotation[][] parameterAnnotations = method.getParameterAnnotations();
            int count = parameterAnnotations.length;
            for (int i = 0; i < count; ++i) {
                boolean isHttpAnnotation = false;
                if (parameterAnnotations[i] != null) {
                    isHttpAnnotation = this.processAnnotationsOnParameter(data, parameterAnnotations[i], i);
                }
                if (parameterTypes[i] == URI.class) {
                    data.urlIndex(i);
                    continue;
                }
                if (isHttpAnnotation) continue;
                Util.checkState(data.formParams().isEmpty(), "Body parameters cannot be used with form parameters.", new Object[0]);
                Util.checkState(data.bodyIndex() == null, "Method has too many Body parameters: %s", method);
                data.bodyIndex(i);
                data.bodyType(Types.resolve(targetType, targetType, genericParameterTypes[i]));
            }
            if (data.headerMapIndex() != null) {
                BaseContract.checkMapString("HeaderMap", parameterTypes[data.headerMapIndex()], genericParameterTypes[data.headerMapIndex()]);
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
            Type[] interfaces;
            Class keyClass = null;
            if (ParameterizedType.class.isAssignableFrom(genericType.getClass())) {
                Type[] parameterTypes = ((ParameterizedType)genericType).getActualTypeArguments();
                keyClass = (Class)parameterTypes[0];
            } else if (genericType instanceof Class && (interfaces = ((Class)genericType).getGenericInterfaces()) != null) {
                for (Type extended : interfaces) {
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

