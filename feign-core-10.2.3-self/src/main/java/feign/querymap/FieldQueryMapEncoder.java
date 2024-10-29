/*
 * Decompiled with CFR 0.152.
 */
package feign.querymap;

import feign.Param;
import feign.QueryMapEncoder;
import feign.codec.EncodeException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class FieldQueryMapEncoder
implements QueryMapEncoder {
    private final Map<Class<?>, ObjectParamMetadata> classToMetadata = new ConcurrentHashMap();

    @Override
    public Map<String, Object> encode(Object object) throws EncodeException {
        ObjectParamMetadata metadata = this.classToMetadata.computeIfAbsent(object.getClass(), x$0 -> ObjectParamMetadata.parseObjectType(x$0));
        return metadata.objectFields.stream().map(field -> this.FieldValuePair(object, (Field)field)).filter(fieldObjectPair -> ((Optional)fieldObjectPair.right).isPresent()).collect(Collectors.toMap(this::fieldName, fieldObjectPair -> ((Optional)fieldObjectPair.right).get()));
    }

    private String fieldName(Pair<Field, Optional<Object>> pair) {
        Param alias = ((Field)pair.left).getAnnotation(Param.class);
        return alias != null ? alias.value() : ((Field)pair.left).getName();
    }

    private Pair<Field, Optional<Object>> FieldValuePair(Object object, Field field) {
        try {
            return Pair.pair(field, Optional.ofNullable(field.get(object)));
        }
        catch (IllegalAccessException e) {
            throw new EncodeException("Failure encoding object into query map", e);
        }
    }

    private static class Pair<T, U> {
        public final T left;
        public final U right;

        private Pair(T left, U right) {
            this.right = right;
            this.left = left;
        }

        public static <T, U> Pair<T, U> pair(T left, U right) {
            return new Pair<T, U>(left, right);
        }
    }

    private static class ObjectParamMetadata {
        private final List<Field> objectFields;

        private ObjectParamMetadata(List<Field> objectFields) {
            this.objectFields = Collections.unmodifiableList(objectFields);
        }

        private static ObjectParamMetadata parseObjectType(Class<?> type) {
            ArrayList allFields = new ArrayList();
            for (Class<?> currentClass = type; currentClass != null; currentClass = currentClass.getSuperclass()) {
                Collections.addAll(allFields, currentClass.getDeclaredFields());
            }
            return new ObjectParamMetadata(allFields.stream().filter(field -> !field.isSynthetic()).peek(field -> field.setAccessible(true)).collect(Collectors.toList()));
        }
    }
}

