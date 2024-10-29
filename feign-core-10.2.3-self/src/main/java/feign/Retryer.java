/*
 * Decompiled with CFR 0.152.
 */
package feign;

import feign.RetryableException;
import java.util.concurrent.TimeUnit;

public interface Retryer
extends Cloneable {
    public static final Retryer NEVER_RETRY = new Retryer(){

        @Override
        public void continueOrPropagate(RetryableException e) {
            throw e;
        }

        @Override
        public Retryer clone() {
            return this;
        }
    };

    public void continueOrPropagate(RetryableException var1);

    public Retryer clone();

    public static class Default
    implements Retryer {
        private final int maxAttempts;
        private final long period;
        private final long maxPeriod;
        int attempt;
        long sleptForMillis;

        public Default() {
            this(100L, TimeUnit.SECONDS.toMillis(1L), 5);
        }

        public Default(long period, long maxPeriod, int maxAttempts) {
            this.period = period;
            this.maxPeriod = maxPeriod;
            this.maxAttempts = maxAttempts;
            this.attempt = 1;
        }

        protected long currentTimeMillis() {
            return System.currentTimeMillis();
        }

        @Override
        public void continueOrPropagate(RetryableException e) {
            long interval;
            if (this.attempt++ >= this.maxAttempts) {
                throw e;
            }
            if (e.retryAfter() != null) {
                interval = e.retryAfter().getTime() - this.currentTimeMillis();
                if (interval > this.maxPeriod) {
                    interval = this.maxPeriod;
                }
                if (interval < 0L) {
                    return;
                }
            } else {
                interval = this.nextMaxInterval();
            }
            try {
                Thread.sleep(interval);
            }
            catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                throw e;
            }
            this.sleptForMillis += interval;
        }

        long nextMaxInterval() {
            long interval = (long)((double)this.period * Math.pow(1.5, this.attempt - 1));
            return interval > this.maxPeriod ? this.maxPeriod : interval;
        }

        @Override
        public Retryer clone() {
            return new Default(this.period, this.maxPeriod, this.maxAttempts);
        }
    }
}

