/*
 * Decompiled with CFR 0.152.
 */
package feign;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public final class Response
implements Closeable {
    private final int status;
    private final String reason;
    private final Map<String, Collection<String>> headers;
    private final Body body;
    private final Request request;

    private Response(Builder builder) {
        Util.checkState(builder.request != null, "original request is required", new Object[0]);
        this.status = builder.status;
        this.request = builder.request;
        this.reason = builder.reason;
        this.headers = builder.headers != null ? Collections.unmodifiableMap(Response.caseInsensitiveCopyOf(builder.headers)) : new LinkedHashMap();
        this.body = builder.body;
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public int status() {
        return this.status;
    }

    public String reason() {
        return this.reason;
    }

    public Map<String, Collection<String>> headers() {
        return this.headers;
    }

    public Body body() {
        return this.body;
    }

    public Request request() {
        return this.request;
    }

    public String toString() {
        StringBuilder builder = new StringBuilder("HTTP/1.1 ").append(this.status);
        if (this.reason != null) {
            builder.append(' ').append(this.reason);
        }
        builder.append('\n');
        for (String field : this.headers.keySet()) {
            for (String value : Util.valuesOrEmpty(this.headers, field)) {
                builder.append(field).append(": ").append(value).append('\n');
            }
        }
        if (this.body != null) {
            builder.append('\n').append(this.body);
        }
        return builder.toString();
    }

    @Override
    public void close() {
        Util.ensureClosed(this.body);
    }

    private static Map<String, Collection<String>> caseInsensitiveCopyOf(Map<String, Collection<String>> headers) {
        TreeMap<String, Collection<String>> result = new TreeMap<String, Collection<String>>(String.CASE_INSENSITIVE_ORDER);
        for (Map.Entry<String, Collection<String>> entry : headers.entrySet()) {
            String headerName = entry.getKey();
            if (!result.containsKey(headerName)) {
                result.put(headerName.toLowerCase(Locale.ROOT), new LinkedList());
            }
            ((Collection)result.get(headerName)).addAll(entry.getValue());
        }
        return result;
    }

    private static final class ByteArrayBody
    implements Body {
        private final byte[] data;

        public ByteArrayBody(byte[] data) {
            this.data = data;
        }

        private static Body orNull(byte[] data) {
            if (data == null) {
                return null;
            }
            return new ByteArrayBody(data);
        }

        private static Body orNull(String text, Charset charset) {
            if (text == null) {
                return null;
            }
            Util.checkNotNull(charset, "charset", new Object[0]);
            return new ByteArrayBody(text.getBytes(charset));
        }

        @Override
        public Integer length() {
            return this.data.length;
        }

        @Override
        public boolean isRepeatable() {
            return true;
        }

        @Override
        public InputStream asInputStream() throws IOException {
            return new ByteArrayInputStream(this.data);
        }

        @Override
        public Reader asReader() throws IOException {
            return new InputStreamReader(this.asInputStream(), Util.UTF_8);
        }

        @Override
        public Reader asReader(Charset charset) throws IOException {
            Util.checkNotNull(charset, "charset should not be null", new Object[0]);
            return new InputStreamReader(this.asInputStream(), charset);
        }

        @Override
        public void close() throws IOException {
        }

        public String toString() {
            return Util.decodeOrDefault(this.data, Util.UTF_8, "Binary data");
        }
    }

    private static final class InputStreamBody
    implements Body {
        private final InputStream inputStream;
        private final Integer length;

        private InputStreamBody(InputStream inputStream, Integer length) {
            this.inputStream = inputStream;
            this.length = length;
        }

        private static Body orNull(InputStream inputStream, Integer length) {
            if (inputStream == null) {
                return null;
            }
            return new InputStreamBody(inputStream, length);
        }

        @Override
        public Integer length() {
            return this.length;
        }

        @Override
        public boolean isRepeatable() {
            return false;
        }

        @Override
        public InputStream asInputStream() throws IOException {
            return this.inputStream;
        }

        @Override
        public Reader asReader() throws IOException {
            return new InputStreamReader(this.inputStream, Util.UTF_8);
        }

        @Override
        public Reader asReader(Charset charset) throws IOException {
            Util.checkNotNull(charset, "charset should not be null", new Object[0]);
            return new InputStreamReader(this.inputStream, charset);
        }

        @Override
        public void close() throws IOException {
            this.inputStream.close();
        }
    }

    public static interface Body
    extends Closeable {
        public Integer length();

        public boolean isRepeatable();

        public InputStream asInputStream() throws IOException;

        public Reader asReader() throws IOException;

        public Reader asReader(Charset var1) throws IOException;
    }

    public static final class Builder {
        int status;
        String reason;
        Map<String, Collection<String>> headers;
        Body body;
        Request request;

        Builder() {
        }

        Builder(Response source) {
            this.status = source.status;
            this.reason = source.reason;
            this.headers = source.headers;
            this.body = source.body;
            this.request = source.request;
        }

        public Builder status(int status) {
            this.status = status;
            return this;
        }

        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }

        public Builder headers(Map<String, Collection<String>> headers) {
            this.headers = headers;
            return this;
        }

        public Builder body(Body body) {
            this.body = body;
            return this;
        }

        public Builder body(InputStream inputStream, Integer length) {
            this.body = InputStreamBody.orNull(inputStream, length);
            return this;
        }

        public Builder body(byte[] data) {
            this.body = ByteArrayBody.orNull(data);
            return this;
        }

        public Builder body(String text, Charset charset) {
            this.body = ByteArrayBody.orNull(text, charset);
            return this;
        }

        public Builder request(Request request) {
            Util.checkNotNull(request, "request is required", new Object[0]);
            this.request = request;
            return this;
        }

        public Response build() {
            return new Response(this);
        }
    }
}

