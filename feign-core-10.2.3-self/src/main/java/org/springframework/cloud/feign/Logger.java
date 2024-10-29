/*
 * Decompiled with CFR 0.152.
 */
package feign;

import feign.Request;
import feign.Response;
import feign.Util;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.FileHandler;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;

public abstract class Logger {
    protected static String methodTag(String configKey) {
        return '[' + configKey.substring(0, configKey.indexOf(40)) + "] ";
    }

    protected abstract void log(String var1, String var2, Object ... var3);

    protected void logRequest(String configKey, Level logLevel, Request request) {
        this.log(configKey, "---> %s %s HTTP/1.1", request.httpMethod().name(), request.url());
        if (logLevel.ordinal() >= Level.HEADERS.ordinal()) {
            for (String field : request.headers().keySet()) {
                for (String value : Util.valuesOrEmpty(request.headers(), field)) {
                    this.log(configKey, "%s: %s", field, value);
                }
            }
            int bodyLength = 0;
            if (request.requestBody().asBytes() != null) {
                bodyLength = request.requestBody().asBytes().length;
                if (logLevel.ordinal() >= Level.FULL.ordinal()) {
                    String bodyText = request.charset() != null ? new String(request.requestBody().asBytes(), request.charset()) : null;
                    this.log(configKey, "", new Object[0]);
                    this.log(configKey, "%s", bodyText != null ? bodyText : "Binary data");
                }
            }
            this.log(configKey, "---> END HTTP (%s-byte body)", bodyLength);
        }
    }

    protected void logRetry(String configKey, Level logLevel) {
        this.log(configKey, "---> RETRYING", new Object[0]);
    }

    protected Response logAndRebufferResponse(String configKey, Level logLevel, Response response, long elapsedTime) throws IOException {
        String reason = response.reason() != null && logLevel.compareTo(Level.NONE) > 0 ? " " + response.reason() : "";
        int status = response.status();
        this.log(configKey, "<--- HTTP/1.1 %s%s (%sms)", status, reason, elapsedTime);
        if (logLevel.ordinal() >= Level.HEADERS.ordinal()) {
            for (String field : response.headers().keySet()) {
                for (String value : Util.valuesOrEmpty(response.headers(), field)) {
                    this.log(configKey, "%s: %s", field, value);
                }
            }
            int bodyLength = 0;
            if (response.body() != null && status != 204 && status != 205) {
                if (logLevel.ordinal() >= Level.FULL.ordinal()) {
                    this.log(configKey, "", new Object[0]);
                }
                byte[] bodyData = Util.toByteArray(response.body().asInputStream());
                bodyLength = bodyData.length;
                if (logLevel.ordinal() >= Level.FULL.ordinal() && bodyLength > 0) {
                    this.log(configKey, "%s", Util.decodeOrDefault(bodyData, Util.UTF_8, "Binary data"));
                }
                this.log(configKey, "<--- END HTTP (%s-byte body)", bodyLength);
                return response.toBuilder().body(bodyData).build();
            }
            this.log(configKey, "<--- END HTTP (%s-byte body)", bodyLength);
        }
        return response;
    }

    protected IOException logIOException(String configKey, Level logLevel, IOException ioe, long elapsedTime) {
        this.log(configKey, "<--- ERROR %s: %s (%sms)", ioe.getClass().getSimpleName(), ioe.getMessage(), elapsedTime);
        if (logLevel.ordinal() >= Level.FULL.ordinal()) {
            StringWriter sw = new StringWriter();
            ioe.printStackTrace(new PrintWriter(sw));
            this.log(configKey, "%s", sw.toString());
            this.log(configKey, "<--- END ERROR", new Object[0]);
        }
        return ioe;
    }

    public static class NoOpLogger
    extends Logger {
        @Override
        protected void logRequest(String configKey, Level logLevel, Request request) {
        }

        @Override
        protected Response logAndRebufferResponse(String configKey, Level logLevel, Response response, long elapsedTime) throws IOException {
            return response;
        }

        @Override
        protected void log(String configKey, String format, Object ... args) {
        }
    }

    public static class JavaLogger
    extends Logger {
        final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(Logger.class.getName());

        @Override
        protected void logRequest(String configKey, Level logLevel, Request request) {
            if (this.logger.isLoggable(java.util.logging.Level.FINE)) {
                super.logRequest(configKey, logLevel, request);
            }
        }

        @Override
        protected Response logAndRebufferResponse(String configKey, Level logLevel, Response response, long elapsedTime) throws IOException {
            if (this.logger.isLoggable(java.util.logging.Level.FINE)) {
                return super.logAndRebufferResponse(configKey, logLevel, response, elapsedTime);
            }
            return response;
        }

        @Override
        protected void log(String configKey, String format, Object ... args) {
            if (this.logger.isLoggable(java.util.logging.Level.FINE)) {
                this.logger.fine(String.format(JavaLogger.methodTag(configKey) + format, args));
            }
        }

        public JavaLogger appendToFile(String logfile) {
            this.logger.setLevel(java.util.logging.Level.FINE);
            try {
                FileHandler handler = new FileHandler(logfile, true);
                handler.setFormatter(new SimpleFormatter(){

                    @Override
                    public String format(LogRecord record) {
                        return String.format("%s%n", record.getMessage());
                    }
                });
                this.logger.addHandler(handler);
            }
            catch (IOException e) {
                throw new IllegalStateException("Could not add file handler.", e);
            }
            return this;
        }
    }

    public static class ErrorLogger
    extends Logger {
        @Override
        protected void log(String configKey, String format, Object ... args) {
            System.err.printf(ErrorLogger.methodTag(configKey) + format + "%n", args);
        }
    }

    public static enum Level {
        NONE,
        BASIC,
        HEADERS,
        FULL;

    }
}

