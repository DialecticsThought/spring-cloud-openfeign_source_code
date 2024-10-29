/*
 * Decompiled with CFR 0.152.
 */
package feign.template;

import feign.template.Expression;
import feign.template.Expressions;
import feign.template.Literal;
import feign.template.TemplateChunk;
import feign.template.UriUtils;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Template {
    private static final Logger logger = Logger.getLogger(Template.class.getName());
    private static final Pattern QUERY_STRING_PATTERN = Pattern.compile("(?<!\\{)(\\?)");
    private final String template;
    private final boolean allowUnresolved;
    private final EncodingOptions encode;
    private final boolean encodeSlash;
    private final Charset charset;
    private final List<TemplateChunk> templateChunks = new ArrayList<TemplateChunk>();

    Template(String value, ExpansionOptions allowUnresolved, EncodingOptions encode, boolean encodeSlash, Charset charset) {
        if (value == null) {
            throw new IllegalArgumentException("template is required.");
        }
        this.template = value;
        this.allowUnresolved = ExpansionOptions.ALLOW_UNRESOLVED == allowUnresolved;
        this.encode = encode;
        this.encodeSlash = encodeSlash;
        this.charset = charset;
        this.parseTemplate();
    }

    Template(ExpansionOptions allowUnresolved, EncodingOptions encode, boolean encodeSlash, Charset charset, List<TemplateChunk> chunks) {
        this.templateChunks.addAll(chunks);
        this.allowUnresolved = ExpansionOptions.ALLOW_UNRESOLVED == allowUnresolved;
        this.encode = encode;
        this.encodeSlash = encodeSlash;
        this.charset = charset;
        this.template = this.toString();
    }

    public String expand(Map<String, ?> variables) {
        if (variables == null) {
            throw new IllegalArgumentException("variable map is required.");
        }
        StringBuilder resolved = null;
        for (TemplateChunk chunk : this.templateChunks) {
            String expanded = chunk instanceof Expression ? this.resolveExpression((Expression)chunk, variables) : chunk.getValue();
            if (expanded == null) continue;
            if (resolved == null) {
                resolved = new StringBuilder();
            }
            resolved.append(expanded);
        }
        if (resolved == null) {
            return null;
        }
        return resolved.toString();
    }

    protected String resolveExpression(Expression expression, Map<String, ?> variables) {
        String resolved = null;
        Object value = variables.get(expression.getName());
        if (value != null) {
            String expanded = expression.expand(value, this.encode.isEncodingRequired());
            if (expanded != null) {
                if (!this.encodeSlash) {
                    logger.fine("Explicit slash decoding specified, decoding all slashes in uri");
                    expanded = expanded.replaceAll("%2F", "/");
                }
                resolved = expanded;
            }
        } else if (this.allowUnresolved) {
            resolved = this.encodeLiteral(expression.toString());
        }
        return resolved;
    }

    private String encodeLiteral(String value) {
        return this.encodeLiteral() ? UriUtils.encode(value, this.charset, true) : value;
    }

    public List<String> getVariables() {
        return this.templateChunks.stream().filter(templateChunk -> Expression.class.isAssignableFrom(templateChunk.getClass())).map(templateChunk -> ((Expression)templateChunk).getName()).filter(Objects::nonNull).collect(Collectors.toList());
    }

    public List<String> getLiterals() {
        return this.templateChunks.stream().filter(templateChunk -> Literal.class.isAssignableFrom(templateChunk.getClass())).map(Object::toString).filter(Objects::nonNull).collect(Collectors.toList());
    }

    public List<TemplateChunk> getTemplateChunks() {
        return Collections.unmodifiableList(this.templateChunks);
    }

    public boolean isLiteral() {
        return this.getVariables().isEmpty();
    }

    private void parseTemplate() {
        this.parseFragment(this.template);
    }

    private void parseFragment(String fragment) {
        ChunkTokenizer tokenizer = new ChunkTokenizer(fragment);
        while (tokenizer.hasNext()) {
            String chunk = tokenizer.next();
            if (chunk.startsWith("{")) {
                Expression expression = Expressions.create(chunk);
                if (expression == null) {
                    this.templateChunks.add(Literal.create(this.encodeLiteral(chunk)));
                    continue;
                }
                this.templateChunks.add(expression);
                continue;
            }
            this.templateChunks.add(Literal.create(this.encodeLiteral(chunk)));
        }
    }

    public String toString() {
        return this.templateChunks.stream().map(TemplateChunk::getValue).collect(Collectors.joining());
    }

    public boolean encodeLiteral() {
        return this.encode.isEncodingRequired();
    }

    boolean encodeSlash() {
        return this.encodeSlash;
    }

    public Charset getCharset() {
        return this.charset;
    }

    public static enum ExpansionOptions {
        ALLOW_UNRESOLVED,
        REQUIRED;

    }

    public static enum EncodingOptions {
        REQUIRED(true),
        NOT_REQUIRED(false);

        private final boolean shouldEncode;

        private EncodingOptions(boolean shouldEncode) {
            this.shouldEncode = shouldEncode;
        }

        public boolean isEncodingRequired() {
            return this.shouldEncode;
        }
    }

    static class ChunkTokenizer {
        private List<String> tokens = new ArrayList<String>();
        private int index;

        ChunkTokenizer(String template) {
            int idx;
            boolean outside = true;
            int level = 0;
            int lastIndex = 0;
            for (idx = 0; idx < template.length(); ++idx) {
                if (template.charAt(idx) == '{') {
                    if (outside) {
                        if (lastIndex < idx) {
                            this.tokens.add(template.substring(lastIndex, idx));
                        }
                        lastIndex = idx;
                        outside = false;
                        continue;
                    }
                    ++level;
                    continue;
                }
                if (template.charAt(idx) != '}' || outside) continue;
                if (level > 0) {
                    --level;
                    continue;
                }
                if (lastIndex < idx) {
                    this.tokens.add(template.substring(lastIndex, idx + 1));
                }
                lastIndex = idx + 1;
                outside = true;
            }
            if (lastIndex < idx) {
                this.tokens.add(template.substring(lastIndex, idx));
            }
        }

        public boolean hasNext() {
            return this.tokens.size() > this.index;
        }

        public String next() {
            if (this.hasNext()) {
                return this.tokens.get(this.index++);
            }
            throw new IllegalStateException("No More Elements");
        }
    }
}

