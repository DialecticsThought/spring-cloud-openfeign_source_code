/*
 * Decompiled with CFR 0.152.
 */
package feign;

import feign.Experimental;
import feign.RequestTemplate;
import feign.Util;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public final class Request
implements Serializable {
    private final HttpMethod httpMethod;
    private final String url;
    private final Map<String, Collection<String>> headers;
    private final Body body;
    private final RequestTemplate requestTemplate;
    private final ProtocolVersion protocolVersion;

    @Deprecated
    public static Request create(String method, String url, Map<String, Collection<String>> headers, byte[] body, Charset charset) {
        Util.checkNotNull(method, "httpMethod of %s", method);
        HttpMethod httpMethod = HttpMethod.valueOf(method.toUpperCase());
        return Request.create(httpMethod, url, headers, body, charset, null);
    }

    @Deprecated
    public static Request create(HttpMethod httpMethod, String url, Map<String, Collection<String>> headers, byte[] body, Charset charset) {
        return Request.create(httpMethod, url, headers, Body.create(body, charset), null);
    }

    public static Request create(HttpMethod httpMethod, String url, Map<String, Collection<String>> headers, byte[] body, Charset charset, RequestTemplate requestTemplate) {
        return Request.create(httpMethod, url, headers, Body.create(body, charset), requestTemplate);
    }

    public static Request create(HttpMethod httpMethod, String url, Map<String, Collection<String>> headers, Body body, RequestTemplate requestTemplate) {
        return new Request(httpMethod, url, headers, body, requestTemplate);
    }

    Request(HttpMethod method, String url, Map<String, Collection<String>> headers, Body body, RequestTemplate requestTemplate) {
        this.httpMethod = Util.checkNotNull(method, "httpMethod of %s", method.name());
        this.url = Util.checkNotNull(url, "url", new Object[0]);
        this.headers = Util.checkNotNull(headers, "headers of %s %s", new Object[]{method, url});
        this.body = body;
        this.requestTemplate = requestTemplate;
        this.protocolVersion = ProtocolVersion.HTTP_1_1;
    }

    @Deprecated
    public String method() {
        return this.httpMethod.name();
    }

    public HttpMethod httpMethod() {
        return this.httpMethod;
    }

    public String url() {
        return this.url;
    }

    public Map<String, Collection<String>> headers() {
        return Collections.unmodifiableMap(this.headers);
    }

    public Charset charset() {
        return this.body.encoding;
    }

    public byte[] body() {
        return this.body.data;
    }

    public boolean isBinary() {
        return this.body.isBinary();
    }

    public int length() {
        return this.body.length();
    }

    public ProtocolVersion protocolVersion() {
        return this.protocolVersion;
    }

    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append((Object)this.httpMethod).append(' ').append(this.url).append(" HTTP/1.1\n");
        for (String field : this.headers.keySet()) {
            for (String value : Util.valuesOrEmpty(this.headers, field)) {
                builder.append(field).append(": ").append(value).append('\n');
            }
        }
        if (this.body != null) {
            builder.append('\n').append(this.body.asString());
        }
        return builder.toString();
    }

    @Experimental
    public RequestTemplate requestTemplate() {
        return this.requestTemplate;
    }

    @Experimental
    public static class Body
    implements Serializable {
        private transient Charset encoding;
        private byte[] data;

        private Body() {
        }

        private Body(byte[] data) {
            this.data = data;
        }

        private Body(byte[] data, Charset encoding) {
            this.data = data;
            this.encoding = encoding;
        }

        public Optional<Charset> getEncoding() {
            return Optional.ofNullable(this.encoding);
        }

        public int length() {
            return this.data != null ? this.data.length : 0;
        }

        public byte[] asBytes() {
            return this.data;
        }

        public String asString() {
            return !this.isBinary() ? new String(this.data, this.encoding) : "Binary data";
        }

        public boolean isBinary() {
            return this.encoding == null || this.data == null;
        }

        public static Body create(String data) {
            return new Body(data.getBytes());
        }

        public static Body create(String data, Charset charset) {
            return new Body(data.getBytes(charset), charset);
        }

        public static Body create(byte[] data) {
            return new Body(data);
        }

        public static Body create(byte[] data, Charset charset) {
            return new Body(data, charset);
        }

        @Deprecated
        public static Body encoded(byte[] data, Charset charset) {
            return Body.create(data, charset);
        }

        public static Body empty() {
            return new Body();
        }
    }

    public static class Options {
        private final long connectTimeout;
        private final TimeUnit connectTimeoutUnit;
        private final long readTimeout;
        private final TimeUnit readTimeoutUnit;
        private final boolean followRedirects;

        @Deprecated
        public Options(int connectTimeoutMillis, int readTimeoutMillis, boolean followRedirects) {
            this(connectTimeoutMillis, TimeUnit.MILLISECONDS, readTimeoutMillis, TimeUnit.MILLISECONDS, followRedirects);
        }

        public Options(long connectTimeout, TimeUnit connectTimeoutUnit, long readTimeout, TimeUnit readTimeoutUnit, boolean followRedirects) {
            this.connectTimeout = connectTimeout;
            this.connectTimeoutUnit = connectTimeoutUnit;
            this.readTimeout = readTimeout;
            this.readTimeoutUnit = readTimeoutUnit;
            this.followRedirects = followRedirects;
        }

        @Deprecated
        public Options(int connectTimeoutMillis, int readTimeoutMillis) {
            this(connectTimeoutMillis, readTimeoutMillis, true);
        }

        public Options() {
            this(10L, TimeUnit.SECONDS, 60L, TimeUnit.SECONDS, true);
        }

        public int connectTimeoutMillis() {
            return (int)this.connectTimeoutUnit.toMillis(this.connectTimeout);
        }

        public int readTimeoutMillis() {
            return (int)this.readTimeoutUnit.toMillis(this.readTimeout);
        }

        public boolean isFollowRedirects() {
            return this.followRedirects;
        }

        public long connectTimeout() {
            return this.connectTimeout;
        }

        public TimeUnit connectTimeoutUnit() {
            return this.connectTimeoutUnit;
        }

        public long readTimeout() {
            return this.readTimeout;
        }

        public TimeUnit readTimeoutUnit() {
            return this.readTimeoutUnit;
        }
    }

    public static enum ProtocolVersion {
        HTTP_1_0("HTTP/1.0"),
        HTTP_1_1("HTTP/1.1"),
        HTTP_2("HTTP/2.0"),
        MOCK;

        final String protocolVersion;

        private ProtocolVersion() {
            this.protocolVersion = this.name();
        }

        private ProtocolVersion(String protocolVersion) {
            this.protocolVersion = protocolVersion;
        }

        public String toString() {
            return this.protocolVersion;
        }
    }

    public static enum HttpMethod {
        GET,
        HEAD,
        POST,
        PUT,
        DELETE,
        CONNECT,
        OPTIONS,
        TRACE,
        PATCH;

    }
}

