/*
 * Decompiled with CFR 0.152.
 */
package org.springframework.cloud.feign.template;

import feign.Util;
import feign.template.Template;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public final class HeaderTemplate
extends Template {
    private Set<String> values;
    private String name;

    public static HeaderTemplate create(String name, Iterable<String> values) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("name is required.");
        }
        if (values == null) {
            throw new IllegalArgumentException("values are required");
        }
        StringBuilder template = new StringBuilder();
        template.append(name).append(" ");
        Iterator<String> iterator = values.iterator();
        while (iterator.hasNext()) {
            template.append(iterator.next());
            if (!iterator.hasNext()) continue;
            template.append(", ");
        }
        return new HeaderTemplate(template.toString(), name, values, Util.UTF_8);
    }

    public static HeaderTemplate append(HeaderTemplate headerTemplate, Iterable<String> values) {
        LinkedHashSet<String> headerValues = new LinkedHashSet<String>(headerTemplate.getValues());
        headerValues.addAll(StreamSupport.stream(values.spliterator(), false).filter(Util::isNotBlank).collect(Collectors.toSet()));
        return HeaderTemplate.create(headerTemplate.getName(), headerValues);
    }

    private HeaderTemplate(String template, String name, Iterable<String> values, Charset charset) {
        super(template, Template.ExpansionOptions.REQUIRED, Template.EncodingOptions.NOT_REQUIRED, false, charset);
        this.values = StreamSupport.stream(values.spliterator(), false).filter(Util::isNotBlank).collect(Collectors.toSet());
        this.name = name;
    }

    public Collection<String> getValues() {
        return Collections.unmodifiableCollection(this.values);
    }

    public String getName() {
        return this.name;
    }
}

