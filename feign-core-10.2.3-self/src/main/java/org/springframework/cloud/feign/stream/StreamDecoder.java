/*
 * Decompiled with CFR 0.152.
 */
package org.springframework.cloud.feign.stream;

import feign.FeignException;
import feign.Response;
import feign.Util;
import feign.codec.Decoder;
import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Iterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public final class StreamDecoder
implements Decoder {
    private final Decoder iteratorDecoder;

    StreamDecoder(Decoder iteratorDecoder) {
        this.iteratorDecoder = iteratorDecoder;
    }

    @Override
    public Object decode(Response response, Type type) throws IOException, FeignException {
        if (!(type instanceof ParameterizedType)) {
            throw new IllegalArgumentException("StreamDecoder supports only stream: unknown " + type);
        }
        ParameterizedType streamType = (ParameterizedType)type;
        if (!Stream.class.equals((Object)streamType.getRawType())) {
            throw new IllegalArgumentException("StreamDecoder supports only stream: unknown " + type);
        }
        Iterator iterator = (Iterator)this.iteratorDecoder.decode(response, new IteratorParameterizedType(streamType));
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, 0), false).onClose(() -> {
            if (iterator instanceof Closeable) {
                Util.ensureClosed((Closeable)((Object)iterator));
            } else {
                Util.ensureClosed(response);
            }
        });
    }

    public static StreamDecoder create(Decoder iteratorDecoder) {
        return new StreamDecoder(iteratorDecoder);
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

