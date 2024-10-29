/*
 * Decompiled with CFR 0.152.
 */
package feign.querymap;

import feign.Param;
import feign.QueryMapEncoder;
import feign.codec.EncodeException;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BeanQueryMapEncoder
implements QueryMapEncoder {
    private final Map<Class<?>, ObjectParamMetadata> classToMetadata = new HashMap();

    @Override
    public Map<String, Object> encode(Object object) throws EncodeException {
        try {
            ObjectParamMetadata metadata = this.getMetadata(object.getClass());
            HashMap<String, Object> propertyNameToValue = new HashMap<String, Object>();
            for (PropertyDescriptor pd : metadata.objectProperties) {
                Method method = pd.getReadMethod();
                Object value = method.invoke(object, new Object[0]);
                if (value == null || value == object) continue;
                Param alias = method.getAnnotation(Param.class);
                String name = alias != null ? alias.value() : pd.getName();
                propertyNameToValue.put(name, value);
            }
            return propertyNameToValue;
        }
        catch (IntrospectionException | IllegalAccessException | InvocationTargetException e) {
            throw new EncodeException("Failure encoding object into query map", e);
        }
    }

    private ObjectParamMetadata getMetadata(Class<?> objectType) throws IntrospectionException {
        ObjectParamMetadata metadata = this.classToMetadata.get(objectType);
        if (metadata == null) {
            metadata = ObjectParamMetadata.parseObjectType(objectType);
            this.classToMetadata.put(objectType, metadata);
        }
        return metadata;
    }

    private static class ObjectParamMetadata {
        private final List<PropertyDescriptor> objectProperties;

        private ObjectParamMetadata(List<PropertyDescriptor> objectProperties) {
            this.objectProperties = Collections.unmodifiableList(objectProperties);
        }

        private static ObjectParamMetadata parseObjectType(Class<?> type) throws IntrospectionException {
            ArrayList<PropertyDescriptor> properties = new ArrayList<PropertyDescriptor>();
            for (PropertyDescriptor pd : Introspector.getBeanInfo(type).getPropertyDescriptors()) {
                boolean isGetterMethod;
                boolean bl = isGetterMethod = pd.getReadMethod() != null && !"class".equals(pd.getName());
                if (!isGetterMethod) continue;
                properties.add(pd);
            }
            return new ObjectParamMetadata(properties);
        }
    }
}

