/*
 * Decompiled with CFR 0.152.
 */
package feign;

import feign.Types;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class Util {
    public static final String CONTENT_LENGTH = "Content-Length";
    public static final String CONTENT_ENCODING = "Content-Encoding";
    public static final String RETRY_AFTER = "Retry-After";
    public static final String ENCODING_GZIP = "gzip";
    public static final String ENCODING_DEFLATE = "deflate";
    public static final Charset UTF_8 = Charset.forName("UTF-8");
    public static final Charset ISO_8859_1 = Charset.forName("ISO-8859-1");
    private static final int BUF_SIZE = 2048;
    public static final Type MAP_STRING_WILDCARD = new Types.ParameterizedTypeImpl(null, (Type)((Object)Map.class), new Type[]{String.class, new Types.WildcardTypeImpl(new Type[]{Object.class}, new Type[0])});
    private static final Map<Class<?>, Supplier<Object>> EMPTIES;

    private Util() {
    }

    public static void checkArgument(boolean expression, String errorMessageTemplate, Object ... errorMessageArgs) {
        if (!expression) {
            throw new IllegalArgumentException(String.format(errorMessageTemplate, errorMessageArgs));
        }
    }

    public static <T> T checkNotNull(T reference, String errorMessageTemplate, Object ... errorMessageArgs) {
        if (reference == null) {
            throw new NullPointerException(String.format(errorMessageTemplate, errorMessageArgs));
        }
        return reference;
    }

    public static void checkState(boolean expression, String errorMessageTemplate, Object ... errorMessageArgs) {
        if (!expression) {
            throw new IllegalStateException(String.format(errorMessageTemplate, errorMessageArgs));
        }
    }

    public static boolean isDefault(Method method) {
        int SYNTHETIC = 4096;
        return (method.getModifiers() & 0x1409) == 1 && method.getDeclaringClass().isInterface();
    }

    public static String emptyToNull(String string) {
        return string == null || string.isEmpty() ? null : string;
    }

    public static <T> T[] removeValues(T[] values, Predicate<T> shouldRemove, Class<T> type) {
        ArrayList<T> collection = new ArrayList<T>(values.length);
        for (T value : values) {
            if (!shouldRemove.negate().test(value)) continue;
            collection.add(value);
        }
        Object[] array = (Object[])Array.newInstance(type, collection.size());
        return collection.toArray(array);
    }

    public static <T> T[] toArray(Iterable<? extends T> iterable, Class<T> type) {
        ArrayList<T> collection;
        if (iterable instanceof Collection) {
            collection = (ArrayList<T>)iterable;
        } else {
            collection = new ArrayList<T>();
            for (T element : iterable) {
                collection.add(element);
            }
        }
        Object[] array = (Object[])Array.newInstance(type, collection.size());
        return collection.toArray(array);
    }

    public static <T> Collection<T> valuesOrEmpty(Map<String, Collection<T>> map, String key) {
        Collection<T> values = map.get(key);
        return values != null ? values : Collections.emptyList();
    }

    public static void ensureClosed(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            }
            catch (IOException iOException) {
                // empty catch block
            }
        }
    }

    public static Type resolveLastTypeParameter(Type genericContext, Class<?> supertype) throws IllegalStateException {
        Type resolvedSuperType = Types.getSupertype(genericContext, Types.getRawType(genericContext), supertype);
        Util.checkState(resolvedSuperType instanceof ParameterizedType, "could not resolve %s into a parameterized type %s", genericContext, supertype);
        Type[] types = ((ParameterizedType)ParameterizedType.class.cast(resolvedSuperType)).getActualTypeArguments();
        for (int i = 0; i < types.length; ++i) {
            Type type = types[i];
            if (!(type instanceof WildcardType)) continue;
            types[i] = ((WildcardType)type).getUpperBounds()[0];
        }
        return types[types.length - 1];
    }

    public static Object emptyValueOf(Type type) {
        return EMPTIES.getOrDefault(Types.getRawType(type), () -> null).get();
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static String toString(Reader reader) throws IOException {
        if (reader == null) {
            return null;
        }
        try {
            CharBuffer charBuf;
            StringBuilder to = new StringBuilder();
            CharBuffer buf = charBuf = CharBuffer.allocate(2048);
            while (reader.read(charBuf) != -1) {
                ((Buffer)buf).flip();
                to.append(charBuf);
                ((Buffer)buf).clear();
            }
            String string = to.toString();
            return string;
        }
        finally {
            Util.ensureClosed(reader);
        }
    }

    public static byte[] toByteArray(InputStream in) throws IOException {
        Util.checkNotNull(in, "in", new Object[0]);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Util.copy(in, out);
            byte[] byArray = out.toByteArray();
            return byArray;
        }
        finally {
            Util.ensureClosed(in);
        }
    }

    private static long copy(InputStream from, OutputStream to) throws IOException {
        int r;
        Util.checkNotNull(from, "from", new Object[0]);
        Util.checkNotNull(to, "to", new Object[0]);
        byte[] buf = new byte[2048];
        long total = 0L;
        while ((r = from.read(buf)) != -1) {
            to.write(buf, 0, r);
            total += (long)r;
        }
        return total;
    }

    public static String decodeOrDefault(byte[] data, Charset charset, String defaultValue) {
        if (data == null) {
            return defaultValue;
        }
        Util.checkNotNull(charset, "charset", new Object[0]);
        try {
            return charset.newDecoder().decode(ByteBuffer.wrap(data)).toString();
        }
        catch (CharacterCodingException ex) {
            return defaultValue;
        }
    }

    public static boolean isNotBlank(String value) {
        return value != null && !value.isEmpty();
    }

    public static boolean isBlank(String value) {
        return value == null || value.isEmpty();
    }

    public static Map<String, Collection<String>> caseInsensitiveCopyOf(Map<String, Collection<String>> map) {
        if (map == null) {
            return Collections.emptyMap();
        }
        TreeMap<String, Collection> result = new TreeMap<String, Collection>(String.CASE_INSENSITIVE_ORDER);
        for (Map.Entry<String, Collection<String>> entry : map.entrySet()) {
            String key2 = entry.getKey();
            if (!result.containsKey(key2)) {
                result.put(key2.toLowerCase(Locale.ROOT), new LinkedList());
            }
            ((Collection)result.get(key2)).addAll(entry.getValue());
        }
        result.replaceAll((key, value) -> Collections.unmodifiableCollection(value));
        return Collections.unmodifiableMap(result);
    }

    public static <T extends Enum<?>> T enumForName(Class<T> enumClass, Object object) {
        String name = Objects.nonNull(object) ? object.toString() : null;
        for (Enum enumItem : (Enum[])enumClass.getEnumConstants()) {
            if (!enumItem.name().equalsIgnoreCase(name) && !enumItem.toString().equalsIgnoreCase(name)) continue;
            return (T)enumItem;
        }
        return null;
    }

    public static List<Field> allFields(Class<?> clazz) {
        if (Objects.equals(clazz, Object.class)) {
            return Collections.emptyList();
        }
        ArrayList<Field> fields = new ArrayList<Field>();
        fields.addAll(Arrays.asList(clazz.getDeclaredFields()));
        fields.addAll(Util.allFields(clazz.getSuperclass()));
        return fields;
    }

    static {
        LinkedHashMap<Class<Stream>, Supplier<Object>> empties = new LinkedHashMap<Class<Stream>, Supplier<Object>>();
        empties.put(Boolean.TYPE, () -> false);
        empties.put(Boolean.class, () -> false);
        empties.put(byte[].class, () -> new byte[0]);
        empties.put(Collection.class, Collections::emptyList);
        empties.put(Iterator.class, Collections::emptyIterator);
        empties.put(List.class, Collections::emptyList);
        empties.put(Map.class, Collections::emptyMap);
        empties.put(Set.class, Collections::emptySet);
        empties.put(Optional.class, Optional::empty);
        empties.put(Stream.class, Stream::empty);
        EMPTIES = Collections.unmodifiableMap(empties);
    }
}

