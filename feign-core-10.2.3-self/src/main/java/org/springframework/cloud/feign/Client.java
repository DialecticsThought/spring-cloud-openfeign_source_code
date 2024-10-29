/*
 * Decompiled with CFR 0.152.
 */
package feign;

import feign.Request;
import feign.Response;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

public interface Client {
    public Response execute(Request var1, Request.Options var2) throws IOException;

    public static class Default
    implements Client {
        private final SSLSocketFactory sslContextFactory;
        private final HostnameVerifier hostnameVerifier;

        public Default(SSLSocketFactory sslContextFactory, HostnameVerifier hostnameVerifier) {
            this.sslContextFactory = sslContextFactory;
            this.hostnameVerifier = hostnameVerifier;
        }

        @Override
        public Response execute(Request request, Request.Options options) throws IOException {
            HttpURLConnection connection = this.convertAndSend(request, options);
            return this.convertResponse(connection, request);
        }

        /*
         * WARNING - Removed try catching itself - possible behaviour change.
         */
        HttpURLConnection convertAndSend(Request request, Request.Options options) throws IOException {
            HttpURLConnection connection = (HttpURLConnection)new URL(request.url()).openConnection();
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
            boolean gzipEncodedRequest = contentEncodingValues != null && contentEncodingValues.contains("gzip");
            boolean deflateEncodedRequest = contentEncodingValues != null && contentEncodingValues.contains("deflate");
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
            if (request.requestBody().asBytes() != null) {
                if (contentLength != null) {
                    connection.setFixedLengthStreamingMode(contentLength);
                } else {
                    connection.setChunkedStreamingMode(8196);
                }
                connection.setDoOutput(true);
                OutputStream out = connection.getOutputStream();
                if (gzipEncodedRequest) {
                    out = new GZIPOutputStream(out);
                } else if (deflateEncodedRequest) {
                    out = new DeflaterOutputStream(out);
                }
                try {
                    out.write(request.requestBody().asBytes());
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

        Response convertResponse(HttpURLConnection connection, Request request) throws IOException {
            int status = connection.getResponseCode();
            String reason = connection.getResponseMessage();
            if (status < 0) {
                throw new IOException(String.format("Invalid status(%s) executing %s %s", status, connection.getRequestMethod(), connection.getURL()));
            }
            LinkedHashMap<String, Collection<String>> headers = new LinkedHashMap<String, Collection<String>>();
            for (Map.Entry<String, List<String>> field : connection.getHeaderFields().entrySet()) {
                if (field.getKey() == null) continue;
                headers.put(field.getKey(), (Collection)field.getValue());
            }
            Integer length = connection.getContentLength();
            if (length == -1) {
                length = null;
            }
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            return Response.builder().status(status).reason(reason).headers(headers).request(request).body(stream, length).build();
        }
    }
}

