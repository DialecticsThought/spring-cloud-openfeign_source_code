/*
 * Decompiled with CFR 0.152.
 */
package feign.stream;

import feign.FeignException;
import feign.Response;
import feign.Util;
import feign.codec.Decoder;
import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Iterator;
import java.util.Optional;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public final class StreamDecoder
implements Decoder {
    private final Decoder iteratorDecoder;
    private final Optional<Decoder> delegateDecoder;

    StreamDecoder(Decoder iteratorDecoder, Decoder delegateDecoder) {
        this.iteratorDecoder = iteratorDecoder;
        this.delegateDecoder = Optional.ofNullable(delegateDecoder);
    }

    @Override
    public Object decode(Response response, Type type) throws IOException, FeignException {
        if (!StreamDecoder.isStream(type)) {
            if (!this.delegateDecoder.isPresent()) {
                throw new IllegalArgumentException("StreamDecoder supports types other than stream. When type is not stream, the delegate decoder needs to be setting.");
            }
            return this.delegateDecoder.get().decode(response, type);
        }
        ParameterizedType streamType = (ParameterizedType)type;
        Iterator iterator = (Iterator)this.iteratorDecoder.decode(response, new IteratorParameterizedType(streamType));
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, 0), false).onClose(() -> {
            if (iterator instanceof Closeable) {
                Util.ensureClosed((Closeable)((Object)iterator));
            } else {
                Util.ensureClosed(response);
            }
        });
    }

    public static boolean isStream(Type type) {
        if (!(type instanceof ParameterizedType)) {
            return false;
        }
        ParameterizedType parameterizedType = (ParameterizedType)type;
        return parameterizedType.getRawType().equals(Stream.class);
    }

    public static StreamDecoder create(Decoder iteratorDecoder) {
        return new StreamDecoder(iteratorDecoder, null);
    }

    public static StreamDecoder create(Decoder iteratorDecoder, Decoder delegateDecoder) {
        return new StreamDecoder(iteratorDecoder, delegateDecoder);
    }

    static final class IteratorParameterizedType
    implements ParameterizedType {
        private final ParameterizedType streamType;

        IteratorParameterizedType(ParameterizedType streamType) {
            this.streamType = streamType;
        }

        @Override
        public Type[] getActualTypeArguments() {
            return this.streamType.getActualTypeArguments();
        }

        @Override
        public Type getRawType() {
            return Iterator.class;
        }

        @Override
        public Type getOwnerType() {
            return null;
        }
    }
}

