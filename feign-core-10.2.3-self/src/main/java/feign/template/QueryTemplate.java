/*
 * Decompiled with CFR 0.152.
 */
package feign.template;

import feign.CollectionFormat;
import feign.Util;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public final class QueryTemplate
extends Template {
    private static final String UNDEF = "undef";
    private List<String> values;
    private final Template name;
    private final CollectionFormat collectionFormat;
    private boolean pure = false;

    public static QueryTemplate create(String name, Iterable<String> values, Charset charset) {
        return QueryTemplate.create(name, values, charset, CollectionFormat.EXPLODED);
    }

    public static QueryTemplate create(String name, Iterable<String> values, Charset charset, CollectionFormat collectionFormat) {
        if (Util.isBlank(name)) {
            throw new IllegalArgumentException("name is required.");
        }
        if (values == null) {
            throw new IllegalArgumentException("values are required");
        }
        Collection remaining = StreamSupport.stream(values.spliterator(), false).filter(Util::isNotBlank).collect(Collectors.toList());
        StringBuilder template = new StringBuilder();
        Iterator iterator = remaining.iterator();
        while (iterator.hasNext()) {
            template.append((String)iterator.next());
            if (!iterator.hasNext()) continue;
            template.append(";");
        }
        return new QueryTemplate(template.toString(), name, remaining, charset, collectionFormat);
    }

    public static QueryTemplate append(QueryTemplate queryTemplate, Iterable<String> values, CollectionFormat collectionFormat) {
        ArrayList<String> queryValues = new ArrayList<String>(queryTemplate.getValues());
        queryValues.addAll(StreamSupport.stream(values.spliterator(), false).filter(Util::isNotBlank).collect(Collectors.toList()));
        return QueryTemplate.create(queryTemplate.getName(), queryValues, queryTemplate.getCharset(), collectionFormat);
    }

    private QueryTemplate(String template, String name, Iterable<String> values, Charset charset, CollectionFormat collectionFormat) {
        super(template, Template.ExpansionOptions.REQUIRED, Template.EncodingOptions.REQUIRED, true, charset);
        this.name = new Template(name, Template.ExpansionOptions.ALLOW_UNRESOLVED, Template.EncodingOptions.REQUIRED, false, charset);
        this.collectionFormat = collectionFormat;
        this.values = StreamSupport.stream(values.spliterator(), false).filter(Util::isNotBlank).collect(Collectors.toList());
        if (this.values.isEmpty()) {
            this.pure = true;
        }
    }

    public List<String> getValues() {
        return this.values;
    }

    public String getName() {
        return this.name.toString();
    }

    @Override
    public String toString() {
        return this.queryString(this.name.toString(), super.toString());
    }

    @Override
    public String expand(Map<String, ?> variables) {
        String name = this.name.expand(variables);
        return this.queryString(name, super.expand(variables));
    }

    @Override
    protected String resolveExpression(Expression expression, Map<String, ?> variables) {
        if (variables.containsKey(expression.getName())) {
            if (variables.get(expression.getName()) == null) {
                return UNDEF;
            }
            return super.resolveExpression(expression, variables);
        }
        return UNDEF;
    }

    private String queryString(String name, String values) {
        if (this.pure) {
            return name;
        }
        List<String> resolved = Arrays.stream(values.split(";")).filter(Objects::nonNull).filter(s -> !UNDEF.equalsIgnoreCase((String)s)).collect(Collectors.toList());
        if (!resolved.isEmpty()) {
            return this.collectionFormat.join(name, resolved, this.getCharset()).toString();
        }
        return null;
    }
}

