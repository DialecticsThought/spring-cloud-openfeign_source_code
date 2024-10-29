/*
 * Decompiled with CFR 0.152.
 */
package feign;

import feign.Contract;
import feign.MethodMetadata;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public abstract class DeclarativeContract
extends Contract.BaseContract {
    private final List<GuardedAnnotationProcessor> classAnnotationProcessors = new ArrayList<GuardedAnnotationProcessor>();
    private final List<GuardedAnnotationProcessor> methodAnnotationProcessors = new ArrayList<GuardedAnnotationProcessor>();
    private final Map<Class<Annotation>, ParameterAnnotationProcessor<Annotation>> parameterAnnotationProcessors = new HashMap<Class<Annotation>, ParameterAnnotationProcessor<Annotation>>();

    @Override
    public final List<MethodMetadata> parseAndValidateMetadata(Class<?> targetType) {
        return super.parseAndValidateMetadata(targetType);
    }

    @Override
    protected final void processAnnotationOnClass(MethodMetadata data, Class<?> targetType) {
        List processors = Arrays.stream(targetType.getAnnotations()).flatMap(annotation -> this.classAnnotationProcessors.stream().filter(processor -> processor.test((Annotation)annotation))).collect(Collectors.toList());
        if (!processors.isEmpty()) {
            Arrays.stream(targetType.getAnnotations()).forEach(annotation -> processors.stream().filter(processor -> processor.test((Annotation)annotation)).forEach(processor -> processor.process((Annotation)annotation, data)));
        } else if (targetType.getAnnotations().length == 0) {
            data.addWarning(String.format("Class %s has no annotations, it may affect contract %s", targetType.getSimpleName(), this.getClass().getSimpleName()));
        } else {
            data.addWarning(String.format("Class %s has annotations %s that are not used by contract %s", targetType.getSimpleName(), Arrays.stream(targetType.getAnnotations()).map(annotation -> annotation.annotationType().getSimpleName()).collect(Collectors.toList()), this.getClass().getSimpleName()));
        }
    }

    @Override
    protected final void processAnnotationOnMethod(MethodMetadata data, Annotation annotation, Method method) {
        List<GuardedAnnotationProcessor> processors = this.methodAnnotationProcessors.stream().filter(processor -> processor.test(annotation)).collect(Collectors.toList());
        if (!processors.isEmpty()) {
            processors.forEach(processor -> processor.process(annotation, data));
        } else {
            data.addWarning(String.format("Method %s has an annotation %s that is not used by contract %s", method.getName(), annotation.annotationType().getSimpleName(), this.getClass().getSimpleName()));
        }
    }

    @Override
    protected final boolean processAnnotationsOnParameter(MethodMetadata data, Annotation[] annotations, int paramIndex) {
        List<Annotation> matchingAnnotations = Arrays.stream(annotations).filter(annotation -> this.parameterAnnotationProcessors.containsKey(annotation.annotationType())).collect(Collectors.toList());
        if (!matchingAnnotations.isEmpty()) {
            matchingAnnotations.forEach(annotation -> this.parameterAnnotationProcessors.getOrDefault(annotation.annotationType(), ParameterAnnotationProcessor.DO_NOTHING).process((Annotation)annotation, data, paramIndex));
        } else {
            String parameterName;
            Parameter parameter = data.method().getParameters()[paramIndex];
            String string = parameterName = parameter.isNamePresent() ? parameter.getName() : parameter.getType().getSimpleName();
            if (annotations.length == 0) {
                data.addWarning(String.format("Parameter %s has no annotations, it may affect contract %s", parameterName, this.getClass().getSimpleName()));
            } else {
                data.addWarning(String.format("Parameter %s has annotations %s that are not used by contract %s", parameterName, Arrays.stream(annotations).map(annotation -> annotation.annotationType().getSimpleName()).collect(Collectors.toList()), this.getClass().getSimpleName()));
            }
        }
        return false;
    }

    protected <E extends Annotation> void registerClassAnnotation(Class<E> annotationType, AnnotationProcessor<E> processor) {
        this.registerClassAnnotation((E annotation) -> annotation.annotationType().equals(annotationType), processor);
    }

    protected <E extends Annotation> void registerClassAnnotation(Predicate<E> predicate, AnnotationProcessor<E> processor) {
        this.classAnnotationProcessors.add(new GuardedAnnotationProcessor(predicate, processor));
    }

    protected <E extends Annotation> void registerMethodAnnotation(Class<E> annotationType, AnnotationProcessor<E> processor) {
        this.registerMethodAnnotation((E annotation) -> annotation.annotationType().equals(annotationType), processor);
    }

    protected <E extends Annotation> void registerMethodAnnotation(Predicate<E> predicate, AnnotationProcessor<E> processor) {
        this.methodAnnotationProcessors.add(new GuardedAnnotationProcessor(predicate, processor));
    }

    protected <E extends Annotation> void registerParameterAnnotation(Class<E> annotation, ParameterAnnotationProcessor<E> processor) {
        this.parameterAnnotationProcessors.put(annotation, processor);
    }

    private class GuardedAnnotationProcessor
    implements Predicate<Annotation>,
    AnnotationProcessor<Annotation> {
        private final Predicate<Annotation> predicate;
        private final AnnotationProcessor<Annotation> processor;

        private GuardedAnnotationProcessor(Predicate predicate, AnnotationProcessor processor) {
            this.predicate = predicate;
            this.processor = processor;
        }

        @Override
        public void process(Annotation annotation, MethodMetadata metadata) {
            this.processor.process(annotation, metadata);
        }

        @Override
        public boolean test(Annotation t) {
            return this.predicate.test(t);
        }
    }

    @FunctionalInterface
    public static interface ParameterAnnotationProcessor<E extends Annotation> {
        public static final ParameterAnnotationProcessor<Annotation> DO_NOTHING = (ann, data, i) -> {};

        public void process(E var1, MethodMetadata var2, int var3);
    }

    @FunctionalInterface
    public static interface AnnotationProcessor<E extends Annotation> {
        public void process(E var1, MethodMetadata var2);
    }
}

