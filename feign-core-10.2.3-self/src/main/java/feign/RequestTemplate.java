/*
 * Decompiled with CFR 0.152.
 */
package feign;

import feign.template.HeaderTemplate;
import feign.template.QueryTemplate;
import feign.template.UriTemplate;
import feign.template.UriUtils;
import java.io.Serializable;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class RequestTemplate
implements Serializable {
    private static final Pattern QUERY_STRING_PATTERN = Pattern.compile("(?<!\\{)\\?");
    private final Map<String, QueryTemplate> queries = new LinkedHashMap<String, QueryTemplate>();
    private final Map<String, HeaderTemplate> headers = new TreeMap<String, HeaderTemplate>(String.CASE_INSENSITIVE_ORDER);
    private String target;
    private String fragment;
    private boolean resolved = false;
    private UriTemplate uriTemplate;
    private Request.HttpMethod method;
    private transient Charset charset = Util.UTF_8;
    private Request.Body body = Request.Body.empty();
    private boolean decodeSlash = true;
    private CollectionFormat collectionFormat = CollectionFormat.EXPLODED;

    public RequestTemplate() {
    }

    private RequestTemplate(String target, String fragment, UriTemplate uriTemplate, Request.HttpMethod method, Charset charset, Request.Body body, boolean decodeSlash, CollectionFormat collectionFormat) {
        this.target = target;
        this.fragment = fragment;
        this.uriTemplate = uriTemplate;
        this.method = method;
        this.charset = charset;
        this.body = body;
        this.decodeSlash = decodeSlash;
        this.collectionFormat = collectionFormat != null ? collectionFormat : CollectionFormat.EXPLODED;
    }

    public static RequestTemplate from(RequestTemplate requestTemplate) {
        RequestTemplate template = new RequestTemplate(requestTemplate.target, requestTemplate.fragment, requestTemplate.uriTemplate, requestTemplate.method, requestTemplate.charset, requestTemplate.body, requestTemplate.decodeSlash, requestTemplate.collectionFormat);
        if (!requestTemplate.queries().isEmpty()) {
            template.queries.putAll(requestTemplate.queries);
        }
        if (!requestTemplate.headers().isEmpty()) {
            template.headers.putAll(requestTemplate.headers);
        }
        return template;
    }

    @Deprecated
    public RequestTemplate(RequestTemplate toCopy) {
        Util.checkNotNull(toCopy, "toCopy", new Object[0]);
        this.target = toCopy.target;
        this.fragment = toCopy.fragment;
        this.method = toCopy.method;
        this.queries.putAll(toCopy.queries);
        this.headers.putAll(toCopy.headers);
        this.charset = toCopy.charset;
        this.body = toCopy.body;
        this.decodeSlash = toCopy.decodeSlash;
        this.collectionFormat = toCopy.collectionFormat != null ? toCopy.collectionFormat : CollectionFormat.EXPLODED;
        this.uriTemplate = toCopy.uriTemplate;
        this.resolved = false;
    }

    public RequestTemplate resolve(Map<String, ?> variables) {
        StringBuilder uri = new StringBuilder();
        RequestTemplate resolved = RequestTemplate.from(this);
        if (this.uriTemplate == null) {
            this.uriTemplate = UriTemplate.create("", !this.decodeSlash, this.charset);
        }
        uri.append(this.uriTemplate.expand(variables));
        if (!this.queries.isEmpty()) {
            resolved.queries(Collections.emptyMap());
            StringBuilder query = new StringBuilder();
            Iterator<QueryTemplate> queryTemplates = this.queries.values().iterator();
            while (queryTemplates.hasNext()) {
                QueryTemplate queryTemplate = queryTemplates.next();
                String queryExpanded = queryTemplate.expand(variables);
                if (!Util.isNotBlank(queryExpanded)) continue;
                query.append(queryExpanded);
                if (!queryTemplates.hasNext()) continue;
                query.append("&");
            }
            String queryString = query.toString();
            if (!queryString.isEmpty()) {
                Matcher queryMatcher = QUERY_STRING_PATTERN.matcher(uri);
                if (queryMatcher.find()) {
                    uri.append("&");
                } else {
                    uri.append("?");
                }
                uri.append(queryString);
            }
        }
        resolved.uri(uri.toString());
        if (!this.headers.isEmpty()) {
            resolved.headers(Collections.emptyMap());
            for (HeaderTemplate headerTemplate : this.headers.values()) {
                String headerValues;
                String header = headerTemplate.expand(variables);
                if (header.isEmpty() || (headerValues = header.substring(header.indexOf(" ") + 1)).isEmpty()) continue;
                resolved.header(headerTemplate.getName(), headerValues);
            }
        }
        resolved.body(this.body.expand(variables));
        resolved.resolved = true;
        return resolved;
    }

    @Deprecated
    RequestTemplate resolve(Map<String, ?> unencoded, Map<String, Boolean> alreadyEncoded) {
        return this.resolve(unencoded);
    }

    public Request request() {
        if (!this.resolved) {
            throw new IllegalStateException("template has not been resolved.");
        }
        return Request.create(this.method, this.url(), this.headers(), this.requestBody());
    }

    @Deprecated
    public RequestTemplate method(String method) {
        Util.checkNotNull(method, "method", new Object[0]);
        try {
            this.method = Request.HttpMethod.valueOf(method);
        }
        catch (IllegalArgumentException iae) {
            throw new IllegalArgumentException("Invalid HTTP Method: " + method);
        }
        return this;
    }

    public RequestTemplate method(Request.HttpMethod method) {
        Util.checkNotNull(method, "method", new Object[0]);
        this.method = method;
        return this;
    }

    public String method() {
        return this.method != null ? this.method.name() : null;
    }

    public RequestTemplate decodeSlash(boolean decodeSlash) {
        this.decodeSlash = decodeSlash;
        this.uriTemplate = UriTemplate.create(this.uriTemplate.toString(), !this.decodeSlash, this.charset);
        return this;
    }

    public boolean decodeSlash() {
        return this.decodeSlash;
    }

    public RequestTemplate collectionFormat(CollectionFormat collectionFormat) {
        this.collectionFormat = collectionFormat;
        return this;
    }

    public CollectionFormat collectionFormat() {
        return this.collectionFormat;
    }

    @Deprecated
    public RequestTemplate append(CharSequence value) {
        if (this.uriTemplate != null) {
            return this.uri(value.toString(), true);
        }
        return this.uri(value.toString());
    }

    @Deprecated
    public RequestTemplate insert(int pos, CharSequence value) {
        return this.target(value.toString());
    }

    public RequestTemplate uri(String uri) {
        return this.uri(uri, false);
    }

    public RequestTemplate uri(String uri, boolean append) {
        int fragmentIndex;
        if (UriUtils.isAbsolute(uri)) {
            throw new IllegalArgumentException("url values must be not be absolute.");
        }
        if (uri == null) {
            uri = "/";
        } else if (!(uri.isEmpty() || uri.startsWith("/") || uri.startsWith("{") || uri.startsWith("?"))) {
            uri = "/" + uri;
        }
        Matcher queryMatcher = QUERY_STRING_PATTERN.matcher(uri);
        if (queryMatcher.find()) {
            String queryString = uri.substring(queryMatcher.start() + 1);
            this.extractQueryTemplates(queryString, append);
            uri = uri.substring(0, queryMatcher.start());
        }
        if ((fragmentIndex = uri.indexOf(35)) > -1) {
            this.fragment = uri.substring(fragmentIndex);
            uri = uri.substring(0, fragmentIndex);
        }
        this.uriTemplate = append && this.uriTemplate != null ? UriTemplate.append(this.uriTemplate, uri) : UriTemplate.create(uri, !this.decodeSlash, this.charset);
        return this;
    }

    public RequestTemplate target(String target) {
        if (Util.isBlank(target)) {
            return this;
        }
        if (!UriUtils.isAbsolute(target)) {
            throw new IllegalArgumentException("target values must be absolute.");
        }
        if (target.endsWith("/")) {
            target = target.substring(0, target.length() - 1);
        }
        try {
            URI targetUri = URI.create(target);
            if (Util.isNotBlank(targetUri.getRawQuery())) {
                this.extractQueryTemplates(targetUri.getRawQuery(), true);
            }
            this.target = targetUri.getScheme() + "://" + targetUri.getAuthority() + targetUri.getPath();
            if (targetUri.getFragment() != null) {
                this.fragment = "#" + targetUri.getFragment();
            }
        }
        catch (IllegalArgumentException iae) {
            throw new IllegalArgumentException("Target is not a valid URI.", iae);
        }
        return this;
    }

    public String url() {
        StringBuilder url = new StringBuilder(this.path());
        if (!this.queries.isEmpty()) {
            url.append(this.queryLine());
        }
        if (this.fragment != null) {
            url.append(this.fragment);
        }
        return url.toString();
    }

    public String path() {
        StringBuilder path = new StringBuilder();
        if (this.target != null) {
            path.append(this.target);
        }
        if (this.uriTemplate != null) {
            path.append(this.uriTemplate.toString());
        }
        if (path.length() == 0) {
            path.append("/");
        }
        return path.toString();
    }

    public List<String> variables() {
        ArrayList<String> variables = new ArrayList<String>(this.uriTemplate.getVariables());
        for (QueryTemplate queryTemplate : this.queries.values()) {
            variables.addAll(queryTemplate.getVariables());
        }
        for (HeaderTemplate headerTemplate : this.headers.values()) {
            variables.addAll(headerTemplate.getVariables());
        }
        variables.addAll(this.body.getVariables());
        return variables;
    }

    public RequestTemplate query(String name, String ... values) {
        if (values == null) {
            return this.query(name, Collections.emptyList());
        }
        return this.query(name, Arrays.asList(values));
    }

    public RequestTemplate query(String name, Iterable<String> values) {
        return this.appendQuery(name, values, this.collectionFormat);
    }

    public RequestTemplate query(String name, Iterable<String> values, CollectionFormat collectionFormat) {
        return this.appendQuery(name, values, collectionFormat);
    }

    private RequestTemplate appendQuery(String name, Iterable<String> values, CollectionFormat collectionFormat) {
        if (!values.iterator().hasNext()) {
            this.queries.remove(name);
            return this;
        }
        this.queries.compute(name, (key, queryTemplate) -> {
            if (queryTemplate == null) {
                return QueryTemplate.create(name, values, this.charset, collectionFormat);
            }
            return QueryTemplate.append(queryTemplate, values, collectionFormat);
        });
        return this;
    }

    public RequestTemplate queries(Map<String, Collection<String>> queries) {
        if (queries == null || queries.isEmpty()) {
            this.queries.clear();
        } else {
            queries.forEach(this::query);
        }
        return this;
    }

    public Map<String, Collection<String>> queries() {
        LinkedHashMap queryMap = new LinkedHashMap();
        this.queries.forEach((key, queryTemplate) -> {
            ArrayList<String> values = new ArrayList<String>(queryTemplate.getValues());
            queryMap.put(key, Collections.unmodifiableList(values));
        });
        return Collections.unmodifiableMap(queryMap);
    }

    public RequestTemplate header(String name, String ... values) {
        return this.header(name, Arrays.asList(values));
    }

    public RequestTemplate header(String name, Iterable<String> values) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("name is required.");
        }
        if (values == null) {
            values = Collections.emptyList();
        }
        return this.appendHeader(name, values);
    }

    private RequestTemplate appendHeader(String name, Iterable<String> values) {
        if (!values.iterator().hasNext()) {
            this.headers.remove(name);
            return this;
        }
        this.headers.compute(name, (headerName, headerTemplate) -> {
            if (headerTemplate == null) {
                return HeaderTemplate.create(headerName, values);
            }
            return HeaderTemplate.append(headerTemplate, values);
        });
        return this;
    }

    public RequestTemplate headers(Map<String, Collection<String>> headers) {
        if (headers != null && !headers.isEmpty()) {
            headers.forEach(this::header);
        } else {
            this.headers.clear();
        }
        return this;
    }

    public Map<String, Collection<String>> headers() {
        TreeMap headerMap = new TreeMap(String.CASE_INSENSITIVE_ORDER);
        this.headers.forEach((key, headerTemplate) -> {
            ArrayList<String> values = new ArrayList<String>(headerTemplate.getValues());
            if (!values.isEmpty()) {
                headerMap.put(key, Collections.unmodifiableList(values));
            }
        });
        return Collections.unmodifiableMap(headerMap);
    }

    @Deprecated
    public RequestTemplate body(byte[] bodyData, Charset charset) {
        this.body(Request.Body.encoded(bodyData, charset));
        return this;
    }

    @Deprecated
    public RequestTemplate body(String bodyText) {
        byte[] bodyData = bodyText != null ? bodyText.getBytes(Util.UTF_8) : null;
        return this.body(bodyData, Util.UTF_8);
    }

    public RequestTemplate body(Request.Body body) {
        this.body = body;
        this.header("Content-Length", new String[0]);
        if (body.length() > 0) {
            this.header("Content-Length", String.valueOf(body.length()));
        }
        return this;
    }

    public Charset requestCharset() {
        return this.charset;
    }

    @Deprecated
    public byte[] body() {
        return this.body.asBytes();
    }

    @Deprecated
    public RequestTemplate bodyTemplate(String bodyTemplate) {
        this.body(Request.Body.bodyTemplate(bodyTemplate, Util.UTF_8));
        return this;
    }

    public String bodyTemplate() {
        return this.body.bodyTemplate();
    }

    public String toString() {
        return this.request().toString();
    }

    public boolean hasRequestVariable(String variable) {
        return this.getRequestVariables().contains(variable);
    }

    public Collection<String> getRequestVariables() {
        LinkedHashSet<String> variables = new LinkedHashSet<String>(this.uriTemplate.getVariables());
        this.queries.values().forEach(queryTemplate -> variables.addAll(queryTemplate.getVariables()));
        this.headers.values().forEach(headerTemplate -> variables.addAll(headerTemplate.getVariables()));
        return variables;
    }

    public boolean resolved() {
        return this.resolved;
    }

    public String queryLine() {
        String result;
        StringBuilder queryString = new StringBuilder();
        if (!this.queries.isEmpty()) {
            Iterator<QueryTemplate> iterator = this.queries.values().iterator();
            while (iterator.hasNext()) {
                QueryTemplate queryTemplate = iterator.next();
                String query = queryTemplate.toString();
                if (query == null || query.isEmpty()) continue;
                queryString.append(query);
                if (!iterator.hasNext()) continue;
                queryString.append("&");
            }
        }
        if ((result = queryString.toString()).endsWith("&")) {
            result = result.substring(0, result.length() - 1);
        }
        if (!result.isEmpty()) {
            result = "?" + result;
        }
        return result;
    }

    private void extractQueryTemplates(String queryString, boolean append) {
        Map queryParameters = Arrays.stream(queryString.split("&")).map(this::splitQueryParameter).collect(Collectors.groupingBy(AbstractMap.SimpleImmutableEntry::getKey, LinkedHashMap::new, Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
        if (!append) {
            this.queries.clear();
        }
        queryParameters.forEach(this::query);
    }

    private AbstractMap.SimpleImmutableEntry<String, String> splitQueryParameter(String pair) {
        int eq = pair.indexOf("=");
        String name = eq > 0 ? pair.substring(0, eq) : pair;
        String value = eq > 0 && eq < pair.length() ? pair.substring(eq + 1) : null;
        return new AbstractMap.SimpleImmutableEntry<String, Object>(name, value);
    }

    public Request.Body requestBody() {
        return this.body;
    }

    static interface Factory {
        public RequestTemplate create(Object[] var1);
    }
}

