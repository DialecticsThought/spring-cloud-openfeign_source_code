/*
 * Decompiled with CFR 0.152.
 */
package feign;

import feign.Request;
import feign.Response;
import feign.Util;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.InflaterInputStream;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

public interface Client {
    public Response execute(Request var1, Request.Options var2) throws IOException;

    public static class Proxied
    extends Default {
        public static final String PROXY_AUTHORIZATION = "Proxy-Authorization";
        private final Proxy proxy;
        private String credentials;

        public Proxied(SSLSocketFactory sslContextFactory, HostnameVerifier hostnameVerifier, Proxy proxy) {
            super(sslContextFactory, hostnameVerifier);
            Util.checkNotNull(proxy, "a proxy is required.", new Object[0]);
            this.proxy = proxy;
        }

        public Proxied(SSLSocketFactory sslContextFactory, HostnameVerifier hostnameVerifier, Proxy proxy, String proxyUser, String proxyPassword) {
            this(sslContextFactory, hostnameVerifier, proxy);
            Util.checkArgument(Util.isNotBlank(proxyUser), "proxy user is required.", new Object[0]);
            Util.checkArgument(Util.isNotBlank(proxyPassword), "proxy password is required.", new Object[0]);
            this.credentials = this.basic(proxyUser, proxyPassword);
        }

        @Override
        public HttpURLConnection getConnection(URL url) throws IOException {
            HttpURLConnection connection = (HttpURLConnection)url.openConnection(this.proxy);
            if (Util.isNotBlank(this.credentials)) {
                connection.addRequestProperty(PROXY_AUTHORIZATION, this.credentials);
            }
            return connection;
        }

        public String getCredentials() {
            return this.credentials;
        }

        private String basic(String username, String password) {
            String token = username + ":" + password;
            byte[] bytes = token.getBytes(StandardCharsets.ISO_8859_1);
            String encoded = Base64.getEncoder().encodeToString(bytes);
            return "Basic " + encoded;
        }
    }

    public static class Default
    implements Client {
        private final SSLSocketFactory sslContextFactory;
        private final HostnameVerifier hostnameVerifier;
        private final boolean disableRequestBuffering;

        public Default(SSLSocketFactory sslContextFactory, HostnameVerifier hostnameVerifier) {
            this.sslContextFactory = sslContextFactory;
            this.hostnameVerifier = hostnameVerifier;
            this.disableRequestBuffering = true;
        }

        public Default(SSLSocketFactory sslContextFactory, HostnameVerifier hostnameVerifier, boolean disableRequestBuffering) {
            this.sslContextFactory = sslContextFactory;
            this.hostnameVerifier = hostnameVerifier;
            this.disableRequestBuffering = disableRequestBuffering;
        }

        @Override
        public Response execute(Request request, Request.Options options) throws IOException {
            HttpURLConnection connection = this.convertAndSend(request, options);
            return this.convertResponse(connection, request);
        }

        Response convertResponse(HttpURLConnection connection, Request request) throws IOException {
            int status = connection.getResponseCode();
            String reason = connection.getResponseMessage();
            if (status < 0) {
                throw new IOException(String.format("Invalid status(%s) executing %s %s", status, connection.getRequestMethod(), connection.getURL()));
            }
            TreeMap<String, Collection<String>> headers = new TreeMap<String, Collection<String>>(String.CASE_INSENSITIVE_ORDER);
            for (Map.Entry<String, List<String>> field : connection.getHeaderFields().entrySet()) {
                if (field.getKey() == null) continue;
                headers.put(field.getKey(), field.getValue());
            }
            Integer length = connection.getContentLength();
            if (length == -1) {
                length = null;
            }
            InputStream stream = status >= 400 ? connection.getErrorStream() : (this.isGzip((Collection)headers.get("Content-Encoding")) ? new GZIPInputStream(connection.getInputStream()) : (this.isDeflate((Collection)headers.get("Content-Encoding")) ? new InflaterInputStream(connection.getInputStream()) : connection.getInputStream()));
            return Response.builder().status(status).reason(reason).headers(headers).request(request).body(stream, length).build();
        }

        public HttpURLConnection getConnection(URL url) throws IOException {
            return (HttpURLConnection)url.openConnection();
        }

        /*
         * WARNING - Removed try catching itself - possible behaviour change.
         */
        HttpURLConnection convertAndSend(Request request, Request.Options options) throws IOException {
            URL url = new URL(request.url());
            HttpURLConnection connection = this.getConnection(url);
            if (connection instanceof HttpsURLConnection) {
                HttpsURLConnection sslCon = (HttpsURLConnection)connection;
                if (this.sslContextFactory != null) {
                    sslCon.setSSLSocketFactory(this.sslContextFactory);
                }
                if (this.hostnameVerifier != null) {
                    sslCon.setHostnameVerifier(this.hostnameVerifier);
                }
            }
            connection.setConnectTimeout(options.connectTimeoutMillis());
            connection.setReadTimeout(options.readTimeoutMillis());
            connection.setAllowUserInteraction(false);
            connection.setInstanceFollowRedirects(options.isFollowRedirects());
            connection.setRequestMethod(request.httpMethod().name());
            Collection<String> contentEncodingValues = request.headers().get("Content-Encoding");
            boolean gzipEncodedRequest = this.isGzip(contentEncodingValues);
            boolean deflateEncodedRequest = this.isDeflate(contentEncodingValues);
            boolean hasAcceptHeader = false;
            Integer contentLength = null;
            for (String field : request.headers().keySet()) {
                if (field.equalsIgnoreCase("Accept")) {
                    hasAcceptHeader = true;
                }
                for (String value : request.headers().get(field)) {
                    if (field.equals("Content-Length")) {
                        if (gzipEncodedRequest || deflateEncodedRequest) continue;
                        contentLength = Integer.valueOf(value);
                        connection.addRequestProperty(field, value);
                        continue;
                    }
                    connection.addRequestProperty(field, value);
                }
            }
            if (!hasAcceptHeader) {
                connection.addRequestProperty("Accept", "*/*");
            }
            if (request.body() != null) {
                if (this.disableRequestBuffering) {
                    if (contentLength != null) {
                        connection.setFixedLengthStreamingMode(contentLength);
                    } else {
                        connection.setChunkedStreamingMode(8196);
                    }
                }
                connection.setDoOutput(true);
                OutputStream out = connection.getOutputStream();
                if (gzipEncodedRequest) {
                    out = new GZIPOutputStream(out);
                } else if (deflateEncodedRequest) {
                    out = new DeflaterOutputStream(out);
                }
                try {
                    out.write(request.body());
                }
                finally {
                    try {
                        out.close();
                    }
                    catch (IOException iOException) {}
                }
            }
            return connection;
        }

        private boolean isGzip(Collection<String> contentEncodingValues) {
            return contentEncodingValues != null && !contentEncodingValues.isEmpty() && contentEncodingValues.contains("gzip");
        }

        private boolean isDeflate(Collection<String> contentEncodingValues) {
            return contentEncodingValues != null && !contentEncodingValues.isEmpty() && contentEncodingValues.contains("deflate");
        }
    }
}

