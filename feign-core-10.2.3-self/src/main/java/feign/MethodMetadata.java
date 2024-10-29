/*
 * Decompiled with CFR 0.152.
 */
package feign;

import feign.Experimental;
import feign.Param;
import feign.RequestTemplate;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class MethodMetadata
implements Serializable {
    private static final long serialVersionUID = 1L;
    private String configKey;
    private transient Type returnType;
    private Integer urlIndex;
    private Integer bodyIndex;
    private Integer headerMapIndex;
    private Integer queryMapIndex;
    private boolean alwaysEncodeBody;
    private transient Type bodyType;
    private final RequestTemplate template = new RequestTemplate();
    private final List<String> formParams = new ArrayList<String>();
    private final Map<Integer, Collection<String>> indexToName = new LinkedHashMap<Integer, Collection<String>>();
    private final Map<Integer, Class<? extends Param.Expander>> indexToExpanderClass = new LinkedHashMap<Integer, Class<? extends Param.Expander>>();
    private final Map<Integer, Boolean> indexToEncoded = new LinkedHashMap<Integer, Boolean>();
    private transient Map<Integer, Param.Expander> indexToExpander;
    private BitSet parameterToIgnore = new BitSet();
    private boolean ignored;
    private transient Class<?> targetType;
    private transient Method method;
    private final transient List<String> warnings = new ArrayList<String>();

    MethodMetadata() {
        this.template.methodMetadata(this);
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

    @Experimental
    public boolean alwaysEncodeBody() {
        return this.alwaysEncodeBody;
    }

    @Experimental
    MethodMetadata alwaysEncodeBody(boolean alwaysEncodeBody) {
        this.alwaysEncodeBody = alwaysEncodeBody;
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

    public MethodMetadata ignoreParamater(int i) {
        this.parameterToIgnore.set(i);
        return this;
    }

    public BitSet parameterToIgnore() {
        return this.parameterToIgnore;
    }

    public MethodMetadata parameterToIgnore(BitSet parameterToIgnore) {
        this.parameterToIgnore = parameterToIgnore;
        return this;
    }

    public boolean shouldIgnoreParamater(int i) {
        return this.parameterToIgnore.get(i);
    }

    public boolean isAlreadyProcessed(Integer index) {
        return index.equals(this.urlIndex) || index.equals(this.bodyIndex) || index.equals(this.headerMapIndex) || index.equals(this.queryMapIndex) || this.indexToName.containsKey(index) || this.indexToExpanderClass.containsKey(index) || this.indexToEncoded.containsKey(index) || this.indexToExpander != null && this.indexToExpander.containsKey(index) || this.parameterToIgnore.get(index);
    }

    public void ignoreMethod() {
        this.ignored = true;
    }

    public boolean isIgnored() {
        return this.ignored;
    }

    @Experimental
    public MethodMetadata targetType(Class<?> targetType) {
        this.targetType = targetType;
        return this;
    }

    @Experimental
    public Class<?> targetType() {
        return this.targetType;
    }

    @Experimental
    public MethodMetadata method(Method method) {
        this.method = method;
        return this;
    }

    @Experimental
    public Method method() {
        return this.method;
    }

    public void addWarning(String warning) {
        this.warnings.add(warning);
    }

    public String warnings() {
        return this.warnings.stream().collect(Collectors.joining("\n- ", "\nWarnings:\n- ", ""));
    }
}

