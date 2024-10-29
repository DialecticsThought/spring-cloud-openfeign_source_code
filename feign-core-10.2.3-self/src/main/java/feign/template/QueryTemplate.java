/*
 * Decompiled with CFR 0.152.
 */
package feign.template;

import feign.CollectionFormat;
import feign.Util;
import feign.template.Template;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public final class QueryTemplate {
    private static final String UNDEF = "undef";
    private List<Template> values = new CopyOnWriteArrayList<Template>();
    private final Template name;
    private final CollectionFormat collectionFormat;
    private boolean pure = false;

    public static QueryTemplate create(String name, Iterable<String> values, Charset charset) {
        return QueryTemplate.create(name, values, charset, CollectionFormat.EXPLODED, true);
    }

    public static QueryTemplate create(String name, Iterable<String> values, Charset charset, CollectionFormat collectionFormat) {
        return QueryTemplate.create(name, values, charset, collectionFormat, true);
    }

    public static QueryTemplate create(String name, Iterable<String> values, Charset charset, CollectionFormat collectionFormat, boolean decodeSlash) {
        if (Util.isBlank(name)) {
            throw new IllegalArgumentException("name is required.");
        }
        if (values == null) {
            throw new IllegalArgumentException("values are required");
        }
        Collection remaining = StreamSupport.stream(values.spliterator(), false).filter(Util::isNotBlank).collect(Collectors.toList());
        return new QueryTemplate(name, remaining, charset, collectionFormat, decodeSlash);
    }

    public static QueryTemplate append(QueryTemplate queryTemplate, Iterable<String> values, CollectionFormat collectionFormat, boolean decodeSlash) {
        ArrayList<String> queryValues = new ArrayList<String>(queryTemplate.getValues());
        queryValues.addAll(StreamSupport.stream(values.spliterator(), false).filter(Util::isNotBlank).collect(Collectors.toList()));
        return QueryTemplate.create(queryTemplate.getName(), queryValues, StandardCharsets.UTF_8, collectionFormat, decodeSlash);
    }

    private QueryTemplate(String name, Iterable<String> values, Charset charset, CollectionFormat collectionFormat, boolean decodeSlash) {
        this.name = new Template(name, Template.ExpansionOptions.ALLOW_UNRESOLVED, Template.EncodingOptions.REQUIRED, !decodeSlash, charset);
        this.collectionFormat = collectionFormat;
        for (String value : values) {
            if (value.isEmpty()) continue;
            this.values.add(new Template(value, Template.ExpansionOptions.REQUIRED, Template.EncodingOptions.REQUIRED, !decodeSlash, charset));
        }
        if (this.values.isEmpty()) {
            this.pure = true;
        }
    }

    public List<String> getValues() {
        return Collections.unmodifiableList(this.values.stream().map(Template::toString).collect(Collectors.toList()));
    }

    public List<String> getVariables() {
        ArrayList<String> variables = new ArrayList<String>(this.name.getVariables());
        for (Template template : this.values) {
            variables.addAll(template.getVariables());
        }
        return Collections.unmodifiableList(variables);
    }

    public String getName() {
        return this.name.toString();
    }

    public String toString() {
        return this.queryString(this.name.toString(), this.getValues());
    }

    public String expand(Map<String, ?> variables) {
        String name = this.name.expand(variables);
        if (this.pure) {
            return name;
        }
        ArrayList<String> expanded = new ArrayList<String>();
        for (Template template : this.values) {
            String result = template.expand(variables);
            if (result == null) continue;
            if (result.contains(",")) {
                expanded.addAll(Arrays.asList(result.split(",")));
                continue;
            }
            expanded.add(result);
        }
        return this.queryString(name, Collections.unmodifiableList(expanded));
    }

    private String queryString(String name, List<String> values) {
        if (this.pure) {
            return name;
        }
        if (!values.isEmpty()) {
            return this.collectionFormat.join(name, values, StandardCharsets.UTF_8).toString();
        }
        return null;
    }
}

