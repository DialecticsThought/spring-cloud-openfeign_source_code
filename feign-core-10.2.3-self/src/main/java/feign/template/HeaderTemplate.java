/*
 * Decompiled with CFR 0.152.
 */
package feign.template;

import feign.Util;
import feign.template.Template;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public final class HeaderTemplate {
    private final String name;
    private final List<Template> values = new CopyOnWriteArrayList<Template>();

    public static HeaderTemplate create(String name, Iterable<String> values) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("name is required.");
        }
        if (values == null) {
            throw new IllegalArgumentException("values are required");
        }
        return new HeaderTemplate(name, values, Util.UTF_8);
    }

    public static HeaderTemplate append(HeaderTemplate headerTemplate, Iterable<String> values) {
        LinkedHashSet<String> headerValues = new LinkedHashSet<String>(headerTemplate.getValues());
        headerValues.addAll(StreamSupport.stream(values.spliterator(), false).filter(Util::isNotBlank).collect(Collectors.toCollection(LinkedHashSet::new)));
        return HeaderTemplate.create(headerTemplate.getName(), headerValues);
    }

    private HeaderTemplate(String name, Iterable<String> values, Charset charset) {
        this.name = name;
        for (String value : values) {
            if (value == null || value.isEmpty()) continue;
            this.values.add(new Template(value, Template.ExpansionOptions.REQUIRED, Template.EncodingOptions.NOT_REQUIRED, false, charset));
        }
    }

    public Collection<String> getValues() {
        return Collections.unmodifiableList(this.values.stream().map(Template::toString).collect(Collectors.toList()));
    }

    public List<String> getVariables() {
        ArrayList<String> variables = new ArrayList<String>();
        for (Template template : this.values) {
            variables.addAll(template.getVariables());
        }
        return Collections.unmodifiableList(variables);
    }

    public String getName() {
        return this.name;
    }

    public String expand(Map<String, ?> variables) {
        ArrayList<String> expanded = new ArrayList<String>();
        if (!this.values.isEmpty()) {
            for (Template template : this.values) {
                String result = template.expand(variables);
                if (result == null) continue;
                expanded.add(result);
            }
        }
        StringBuilder result = new StringBuilder();
        if (!expanded.isEmpty()) {
            result.append(String.join((CharSequence)", ", expanded));
        }
        return result.toString();
    }
}

