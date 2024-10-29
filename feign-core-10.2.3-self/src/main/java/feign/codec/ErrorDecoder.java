/*
 * Decompiled with CFR 0.152.
 */
package feign.codec;

import feign.FeignException;
import feign.Response;
import feign.RetryableException;
import feign.Util;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public interface ErrorDecoder {
    public Exception decode(String var1, Response var2);

    public static class RetryAfterDecoder {
        static final DateFormat RFC822_FORMAT = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US);
        private final DateFormat rfc822Format;

        RetryAfterDecoder() {
            this(RFC822_FORMAT);
        }

        RetryAfterDecoder(DateFormat rfc822Format) {
            this.rfc822Format = Util.checkNotNull(rfc822Format, "rfc822Format", new Object[0]);
        }

        protected long currentTimeMillis() {
            return System.currentTimeMillis();
        }

        /*
         * WARNING - Removed try catching itself - possible behaviour change.
         */
        public Date apply(String retryAfter) {
            if (retryAfter == null) {
                return null;
            }
            if (retryAfter.matches("^[0-9]+\\.?0*$")) {
                retryAfter = retryAfter.replaceAll("\\.0*$", "");
                long deltaMillis = TimeUnit.SECONDS.toMillis(Long.parseLong(retryAfter));
                return new Date(this.currentTimeMillis() + deltaMillis);
            }
            DateFormat dateFormat = this.rfc822Format;
            synchronized (dateFormat) {
                try {
                    return this.rfc822Format.parse(retryAfter);
                }
                catch (ParseException ignored) {
                    return null;
                }
            }
        }
    }

    public static class Default
    implements ErrorDecoder {
        private final RetryAfterDecoder retryAfterDecoder = new RetryAfterDecoder();

        @Override
        public Exception decode(String methodKey, Response response) {
            FeignException exception = FeignException.errorStatus(methodKey, response);
            Date retryAfter = this.retryAfterDecoder.apply((String)this.firstOrNull(response.headers(), "Retry-After"));
            if (retryAfter != null) {
                return new RetryableException(response.status(), exception.getMessage(), response.request().httpMethod(), (Throwable)exception, retryAfter, response.request());
            }
            return exception;
        }

        private <T> T firstOrNull(Map<String, Collection<T>> map, String key) {
            if (map.containsKey(key) && !map.get(key).isEmpty()) {
                return map.get(key).iterator().next();
            }
            return null;
        }
    }
}

