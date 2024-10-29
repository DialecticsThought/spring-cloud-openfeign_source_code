/*
 * Decompiled with CFR 0.152.
 */
package feign;

import feign.template.BodyTemplate;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class Request {
    private final HttpMethod httpMethod;
    private final String url;
    private final Map<String, Collection<String>> headers;
    private final Body body;

    public static Request create(String method, String url, Map<String, Collection<String>> headers, byte[] body, Charset charset) {
        Util.checkNotNull(method, "httpMethod of %s", method);
        HttpMethod httpMethod = HttpMethod.valueOf(method.toUpperCase());
        return Request.create(httpMethod, url, headers, body, charset);
    }

    public static Request create(HttpMethod httpMethod, String url, Map<String, Collection<String>> headers, byte[] body, Charset charset) {
        return Request.create(httpMethod, url, headers, Body.encoded(body, charset));
    }

    public static Request create(HttpMethod httpMethod, String url, Map<String, Collection<String>> headers, Body body) {
        return new Request(httpMethod, url, headers, body);
    }

    Request(HttpMethod method, String url, Map<String, Collection<String>> headers, Body body) {
        this.httpMethod = Util.checkNotNull(method, "httpMethod of %s", method.name());
        this.url = Util.checkNotNull(url, "url", new Object[0]);
        this.headers = Util.checkNotNull(headers, "headers of %s %s", new Object[]{method, url});
        this.body = body;
    }

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
        return this.headers;
    }

    public Charset charset() {
        return this.body.encoding;
    }

    public byte[] body() {
        return this.body.data;
    }

    public Body requestBody() {
        return this.body;
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

    public static class Options {
        private final int connectTimeoutMillis;
        private final int readTimeoutMillis;
        private final boolean followRedirects;

        public Options(int connectTimeoutMillis, int readTimeoutMillis, boolean followRedirects) {
            this.connectTimeoutMillis = connectTimeoutMillis;
            this.readTimeoutMillis = readTimeoutMillis;
            this.followRedirects = followRedirects;
        }

        public Options(int connectTimeoutMillis, int readTimeoutMillis) {
            this(connectTimeoutMillis, readTimeoutMillis, true);
        }

        public Options() {
            this(10000, 60000);
        }

        public int connectTimeoutMillis() {
            return this.connectTimeoutMillis;
        }

        public int readTimeoutMillis() {
            return this.readTimeoutMillis;
        }

        public boolean isFollowRedirects() {
            return this.followRedirects;
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

    public static class Body {
        private final byte[] data;
        private final Charset encoding;
        private final BodyTemplate bodyTemplate;

        private Body(byte[] data, Charset encoding, BodyTemplate bodyTemplate) {
            this.data = data;
            this.encoding = encoding;
            this.bodyTemplate = bodyTemplate;
        }

        public Body expand(Map<String, ?> variables) {
            if (this.bodyTemplate == null) {
                return this;
            }
            return Body.encoded(this.bodyTemplate.expand(variables).getBytes(this.encoding), this.encoding);
        }

        public List<String> getVariables() {
            if (this.bodyTemplate == null) {
                return Collections.emptyList();
            }
            return this.bodyTemplate.getVariables();
        }

        public static Body encoded(byte[] bodyData, Charset encoding) {
            return new Body(bodyData, encoding, null);
        }

        public int length() {
            return this.data != null ? this.data.length : 0;
        }

        public byte[] asBytes() {
            return this.data;
        }

        public static Body bodyTemplate(String bodyTemplate, Charset encoding) {
            return new Body(null, encoding, BodyTemplate.create(bodyTemplate));
        }

        public String bodyTemplate() {
            return this.bodyTemplate != null ? this.bodyTemplate.toString() : null;
        }

        public String asString() {
            return this.encoding != null && this.data != null ? new String(this.data, this.encoding) : "Binary data";
        }

        public static Body empty() {
            return new Body(null, null, null);
        }
    }
}

