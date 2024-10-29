/*
 * Decompiled with CFR 0.152.
 */
package feign;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(value=RetentionPolicy.RUNTIME)
@Target(value={ElementType.PARAMETER, ElementType.FIELD, ElementType.METHOD})
public @interface Param {
    public String value() default "";

    public Class<? extends Expander> expander() default ToStringExpander.class;

    public boolean encoded() default false;

    public static final class ToStringExpander
    implements Expander {
        @Override
        public String expand(Object value) {
            return value.toString();
        }
    }

    public static interface Expander {
        public String expand(Object var1);
    }
}

