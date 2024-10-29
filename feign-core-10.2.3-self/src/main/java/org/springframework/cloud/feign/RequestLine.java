/*
 * Decompiled with CFR 0.152.
 */
package feign;

import feign.CollectionFormat;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(value={ElementType.METHOD})
@Retention(value=RetentionPolicy.RUNTIME)
public @interface RequestLine {
    public String value();

    public boolean decodeSlash() default true;

    public CollectionFormat collectionFormat() default CollectionFormat.EXPLODED;
}

