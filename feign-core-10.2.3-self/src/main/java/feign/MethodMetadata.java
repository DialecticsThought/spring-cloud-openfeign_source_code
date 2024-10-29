/*
 * Decompiled with CFR 0.152.
 */
package feign;

import java.io.Serializable;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MethodMetadata
implements Serializable {
    private static final long serialVersionUID = 1L;
    private String configKey;
    private transient Type returnType;
    private Integer urlIndex;
    private Integer bodyIndex;
    private Integer headerMapIndex;
    private Integer queryMapIndex;
    private boolean queryMapEncoded;
    private transient Type bodyType;
    private RequestTemplate template = new RequestTemplate();
    private List<String> formParams = new ArrayList<String>();
    private Map<Integer, Collection<String>> indexToName = new LinkedHashMap<Integer, Collection<String>>();
    private Map<Integer, Class<? extends Param.Expander>> indexToExpanderClass = new LinkedHashMap<Integer, Class<? extends Param.Expander>>();
    private Map<Integer, Boolean> indexToEncoded = new LinkedHashMap<Integer, Boolean>();
    private transient Map<Integer, Param.Expander> indexToExpander;

    MethodMetadata() {
    }

    public String configKey() {
        return this.configKey;
    }

    public MethodMetadata configKey(String configKey) {
        this.configKey = configKey;
        return this;
    }

    public Type returnType() {
        return this.returnType;
    }

    public MethodMetadata returnType(Type returnType) {
        this.returnType = returnType;
        return this;
    }

    public Integer urlIndex() {
        return this.urlIndex;
    }

    public MethodMetadata urlIndex(Integer urlIndex) {
        this.urlIndex = urlIndex;
        return this;
    }

    public Integer bodyIndex() {
        return this.bodyIndex;
    }

    public MethodMetadata bodyIndex(Integer bodyIndex) {
        this.bodyIndex = bodyIndex;
        return this;
    }

    public Integer headerMapIndex() {
        return this.headerMapIndex;
    }

    public MethodMetadata headerMapIndex(Integer headerMapIndex) {
        this.headerMapIndex = headerMapIndex;
        return this;
    }

    public Integer queryMapIndex() {
        return this.queryMapIndex;
    }

    public MethodMetadata queryMapIndex(Integer queryMapIndex) {
        this.queryMapIndex = queryMapIndex;
        return this;
    }

    public boolean queryMapEncoded() {
        return this.queryMapEncoded;
    }

    public MethodMetadata queryMapEncoded(boolean queryMapEncoded) {
        this.queryMapEncoded = queryMapEncoded;
        return this;
    }

    public Type bodyType() {
        return this.bodyType;
    }

    public MethodMetadata bodyType(Type bodyType) {
        this.bodyType = bodyType;
        return this;
    }

    public RequestTemplate template() {
        return this.template;
    }

    public List<String> formParams() {
        return this.formParams;
    }

    public Map<Integer, Collection<String>> indexToName() {
        return this.indexToName;
    }

    public Map<Integer, Boolean> indexToEncoded() {
        return this.indexToEncoded;
    }

    public Map<Integer, Class<? extends Param.Expander>> indexToExpanderClass() {
        return this.indexToExpanderClass;
    }

    public MethodMetadata indexToExpander(Map<Integer, Param.Expander> indexToExpander) {
        this.indexToExpander = indexToExpander;
        return this;
    }

    public Map<Integer, Param.Expander> indexToExpander() {
        return this.indexToExpander;
    }
}

