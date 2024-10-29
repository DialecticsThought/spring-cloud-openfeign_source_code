/*
 * Decompiled with CFR 0.152.
 */
package feign.querymap;

import feign.QueryMapEncoder;
import feign.codec.EncodeException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class FieldQueryMapEncoder
implements QueryMapEncoder {
    private final Map<Class<?>, ObjectParamMetadata> classToMetadata = new HashMap();

    @Override
    public Map<String, Object> encode(Object object) throws EncodeException {
        try {
            ObjectParamMetadata metadata = this.getMetadata(object.getClass());
            HashMap<String, Object> fieldNameToValue = new HashMap<String, Object>();
            for (Field field : metadata.objectFields) {
                Object value = field.get(object);
                if (value == null || value == object) continue;
                fieldNameToValue.put(field.getName(), value);
            }
            return fieldNameToValue;
        }
        catch (IllegalAccessException e) {
            throw new EncodeException("Failure encoding object into query map", e);
        }
    }

    private ObjectParamMetadata getMetadata(Class<?> objectType) {
        ObjectParamMetadata metadata = this.classToMetadata.get(objectType);
        if (metadata == null) {
            metadata = ObjectParamMetadata.parseObjectType(objectType);
            this.classToMetadata.put(objectType, metadata);
        }
        return metadata;
    }

    private static class ObjectParamMetadata {
        private final List<Field> objectFields;

        private ObjectParamMetadata(List<Field> objectFields) {
            this.objectFields = Collections.unmodifiableList(objectFields);
        }

        private static ObjectParamMetadata parseObjectType(Class<?> type) {
            return new ObjectParamMetadata(Arrays.stream(type.getDeclaredFields()).filter(field -> !field.isSynthetic()).peek(field -> field.setAccessible(true)).collect(Collectors.toList()));
        }
    }
}

