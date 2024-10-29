/*
 * Decompiled with CFR 0.152.
 */
package feign;

import feign.Experimental;
import feign.Request;
import feign.RequestTemplate;
import feign.Util;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;

public final class Response
implements Closeable {
    private final int status;
    private final String reason;
    private final Map<String, Collection<String>> headers;
    private final Body body;
    private final Request request;
    private final Request.ProtocolVersion protocolVersion;

    private Response(Builder builder) {
        Util.checkState(builder.request != null, "original request is required", new Object[0]);
        this.status = builder.status;
        this.request = builder.request;
        this.reason = builder.reason;
        this.headers = Util.caseInsensitiveCopyOf(builder.headers);
        this.body = builder.body;
        this.protocolVersion = builder.protocolVersion;
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

    public Request.ProtocolVersion protocolVersion() {
        return this.protocolVersion;
    }

    public Charset charset() {
        Collection<String> contentTypeHeaders = this.headers().get("Content-Type");
        if (contentTypeHeaders != null) {
            for (String contentTypeHeader : contentTypeHeaders) {
                String[] charsetParts;
                String[] contentTypeParmeters = contentTypeHeader.split(";");
                if (contentTypeParmeters.length <= 1 || (charsetParts = contentTypeParmeters[1].split("=")).length != 2 || !"charset".equalsIgnoreCase(charsetParts[0].trim())) continue;
                return Charset.forName(charsetParts[1]);
            }
        }
        return Util.UTF_8;
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
        public InputStream asInputStream() {
            return this.inputStream;
        }

        @Override
        public Reader asReader() {
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

        @Deprecated
        default public Reader asReader() throws IOException {
            return this.asReader(StandardCharsets.UTF_8);
        }

        public Reader asReader(Charset var1) throws IOException;
    }

    public static final class Builder {
        int status;
        String reason;
        Map<String, Collection<String>> headers;
        Body body;
        Request request;
        private RequestTemplate requestTemplate;
        private Request.ProtocolVersion protocolVersion = Request.ProtocolVersion.HTTP_1_1;

        Builder() {
        }

        Builder(Response source) {
            this.status = source.status;
            this.reason = source.reason;
            this.headers = source.headers;
            this.body = source.body;
            this.request = source.request;
            this.protocolVersion = source.protocolVersion;
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

        public Builder protocolVersion(Request.ProtocolVersion protocolVersion) {
            this.protocolVersion = protocolVersion;
            return this;
        }

        @Experimental
        public Builder requestTemplate(RequestTemplate requestTemplate) {
            this.requestTemplate = requestTemplate;
            return this;
        }

        public Response build() {
            return new Response(this);
        }
    }
}

