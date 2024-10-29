/*
 * Decompiled with CFR 0.152.
 */
package feign;

public interface Target<T> {
    public Class<T> type();

    public String name();

    public String url();

    public Request apply(RequestTemplate var1);

    public static final class EmptyTarget<T>
    implements Target<T> {
        private final Class<T> type;
        private final String name;

        EmptyTarget(Class<T> type, String name) {
            this.type = Util.checkNotNull(type, "type", new Object[0]);
            this.name = Util.checkNotNull(Util.emptyToNull(name), "name", new Object[0]);
        }

        public static <T> EmptyTarget<T> create(Class<T> type) {
            return new EmptyTarget<T>(type, "empty:" + type.getSimpleName());
        }

        public static <T> EmptyTarget<T> create(Class<T> type, String name) {
            return new EmptyTarget<T>(type, name);
        }

        @Override
        public Class<T> type() {
            return this.type;
        }

        @Override
        public String name() {
            return this.name;
        }

        @Override
        public String url() {
            throw new UnsupportedOperationException("Empty targets don't have URLs");
        }

        @Override
        public Request apply(RequestTemplate input) {
            if (input.url().indexOf("http") != 0) {
                throw new UnsupportedOperationException("Request with non-absolute URL not supported with empty target");
            }
            return input.request();
        }

        public boolean equals(Object obj) {
            if (obj instanceof EmptyTarget) {
                EmptyTarget other = (EmptyTarget)obj;
                return this.type.equals(other.type) && this.name.equals(other.name);
            }
            return false;
        }

        public int hashCode() {
            int result = 17;
            result = 31 * result + this.type.hashCode();
            result = 31 * result + this.name.hashCode();
            return result;
        }

        public String toString() {
            if (this.name.equals("empty:" + this.type.getSimpleName())) {
                return "EmptyTarget(type=" + this.type.getSimpleName() + ")";
            }
            return "EmptyTarget(type=" + this.type.getSimpleName() + ", name=" + this.name + ")";
        }
    }

    public static class HardCodedTarget<T>
    implements Target<T> {
        private final Class<T> type;
        private final String name;
        private final String url;

        public HardCodedTarget(Class<T> type, String url) {
            this(type, url, url);
        }

        public HardCodedTarget(Class<T> type, String name, String url) {
            this.type = Util.checkNotNull(type, "type", new Object[0]);
            this.name = Util.checkNotNull(Util.emptyToNull(name), "name", new Object[0]);
            this.url = Util.checkNotNull(Util.emptyToNull(url), "url", new Object[0]);
        }

        @Override
        public Class<T> type() {
            return this.type;
        }

        @Override
        public String name() {
            return this.name;
        }

        @Override
        public String url() {
            return this.url;
        }

        @Override
        public Request apply(RequestTemplate input) {
            if (input.url().indexOf("http") != 0) {
                input.target(this.url());
            }
            return input.request();
        }

        public boolean equals(Object obj) {
            if (obj instanceof HardCodedTarget) {
                HardCodedTarget other = (HardCodedTarget)obj;
                return this.type.equals(other.type) && this.name.equals(other.name) && this.url.equals(other.url);
            }
            return false;
        }

        public int hashCode() {
            int result = 17;
            result = 31 * result + this.type.hashCode();
            result = 31 * result + this.name.hashCode();
            result = 31 * result + this.url.hashCode();
            return result;
        }

        public String toString() {
            if (this.name.equals(this.url)) {
                return "HardCodedTarget(type=" + this.type.getSimpleName() + ", url=" + this.url + ")";
            }
            return "HardCodedTarget(type=" + this.type.getSimpleName() + ", name=" + this.name + ", url=" + this.url + ")";
        }
    }
}

