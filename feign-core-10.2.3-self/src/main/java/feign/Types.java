/*
 * Decompiled with CFR 0.152.
 */
package feign;

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.NoSuchElementException;

public final class Types {
    private static final Type[] EMPTY_TYPE_ARRAY = new Type[0];

    private Types() {
    }

    public static Class<?> getRawType(Type type) {
        if (type instanceof Class) {
            return (Class)type;
        }
        if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType)type;
            Type rawType = parameterizedType.getRawType();
            if (!(rawType instanceof Class)) {
                throw new IllegalArgumentException();
            }
            return (Class)rawType;
        }
        if (type instanceof GenericArrayType) {
            Type componentType = ((GenericArrayType)type).getGenericComponentType();
            return Array.newInstance(Types.getRawType(componentType), 0).getClass();
        }
        if (type instanceof TypeVariable) {
            return Object.class;
        }
        if (type instanceof WildcardType) {
            return Types.getRawType(((WildcardType)type).getUpperBounds()[0]);
        }
        String className = type == null ? "null" : type.getClass().getName();
        throw new IllegalArgumentException("Expected a Class, ParameterizedType, or GenericArrayType, but <" + type + "> is of type " + className);
    }

    static boolean equals(Type a, Type b) {
        if (a == b) {
            return true;
        }
        if (a instanceof Class) {
            return a.equals(b);
        }
        if (a instanceof ParameterizedType) {
            if (!(b instanceof ParameterizedType)) {
                return false;
            }
            ParameterizedType pa = (ParameterizedType)a;
            ParameterizedType pb = (ParameterizedType)b;
            return Types.equal(pa.getOwnerType(), pb.getOwnerType()) && pa.getRawType().equals(pb.getRawType()) && Arrays.equals(pa.getActualTypeArguments(), pb.getActualTypeArguments());
        }
        if (a instanceof GenericArrayType) {
            if (!(b instanceof GenericArrayType)) {
                return false;
            }
            GenericArrayType ga = (GenericArrayType)a;
            GenericArrayType gb = (GenericArrayType)b;
            return Types.equals(ga.getGenericComponentType(), gb.getGenericComponentType());
        }
        if (a instanceof WildcardType) {
            if (!(b instanceof WildcardType)) {
                return false;
            }
            WildcardType wa = (WildcardType)a;
            WildcardType wb = (WildcardType)b;
            return Arrays.equals(wa.getUpperBounds(), wb.getUpperBounds()) && Arrays.equals(wa.getLowerBounds(), wb.getLowerBounds());
        }
        if (a instanceof TypeVariable) {
            if (!(b instanceof TypeVariable)) {
                return false;
            }
            TypeVariable va = (TypeVariable)a;
            TypeVariable vb = (TypeVariable)b;
            return va.getGenericDeclaration() == vb.getGenericDeclaration() && va.getName().equals(vb.getName());
        }
        return false;
    }

    static Type getGenericSupertype(Type context, Class<?> rawType, Class<?> toResolve) {
        if (toResolve == rawType) {
            return context;
        }
        if (toResolve.isInterface()) {
            Class<?>[] interfaces = rawType.getInterfaces();
            int length = interfaces.length;
            for (int i = 0; i < length; ++i) {
                if (interfaces[i] == toResolve) {
                    return rawType.getGenericInterfaces()[i];
                }
                if (!toResolve.isAssignableFrom(interfaces[i])) continue;
                return Types.getGenericSupertype(rawType.getGenericInterfaces()[i], interfaces[i], toResolve);
            }
        }
        if (!rawType.isInterface()) {
            while (rawType != Object.class) {
                Class<?> rawSupertype = rawType.getSuperclass();
                if (rawSupertype == toResolve) {
                    return rawType.getGenericSuperclass();
                }
                if (toResolve.isAssignableFrom(rawSupertype)) {
                    return Types.getGenericSupertype(rawType.getGenericSuperclass(), rawSupertype, toResolve);
                }
                rawType = rawSupertype;
            }
        }
        return toResolve;
    }

    private static int indexOf(Object[] array, Object toFind) {
        for (int i = 0; i < array.length; ++i) {
            if (!toFind.equals(array[i])) continue;
            return i;
        }
        throw new NoSuchElementException();
    }

    private static boolean equal(Object a, Object b) {
        return a == b || a != null && a.equals(b);
    }

    private static int hashCodeOrZero(Object o) {
        return o != null ? o.hashCode() : 0;
    }

    static String typeToString(Type type) {
        return type instanceof Class ? ((Class)type).getName() : type.toString();
    }

    static Type getSupertype(Type context, Class<?> contextRawType, Class<?> supertype) {
        if (!supertype.isAssignableFrom(contextRawType)) {
            throw new IllegalArgumentException();
        }
        return Types.resolve(context, contextRawType, Types.getGenericSupertype(context, contextRawType, supertype));
    }

    static Type resolve(Type context, Class<?> contextRawType, Type toResolve) {
        Type original;
        while (toResolve instanceof TypeVariable) {
            TypeVariable typeVariable = (TypeVariable)toResolve;
            if ((toResolve = Types.resolveTypeVariable(context, contextRawType, typeVariable)) != typeVariable) continue;
            return toResolve;
        }
        if (toResolve instanceof Class && ((Class)toResolve).isArray()) {
            Type newComponentType;
            original = (Class)toResolve;
            Class<?> componentType = ((Class)original).getComponentType();
            return componentType == (newComponentType = Types.resolve(context, contextRawType, componentType)) ? original : new GenericArrayTypeImpl(newComponentType);
        }
        if (toResolve instanceof GenericArrayType) {
            Type newComponentType;
            original = (GenericArrayType)toResolve;
            Type componentType = original.getGenericComponentType();
            return componentType == (newComponentType = Types.resolve(context, contextRawType, componentType)) ? original : new GenericArrayTypeImpl(newComponentType);
        }
        if (toResolve instanceof ParameterizedType) {
            original = (ParameterizedType)toResolve;
            Type ownerType = original.getOwnerType();
            Type newOwnerType = Types.resolve(context, contextRawType, ownerType);
            boolean changed = newOwnerType != ownerType;
            Type[] args = original.getActualTypeArguments();
            int length = args.length;
            for (int t = 0; t < length; ++t) {
                Type resolvedTypeArgument = Types.resolve(context, contextRawType, args[t]);
                if (resolvedTypeArgument == args[t]) continue;
                if (!changed) {
                    args = (Type[])args.clone();
                    changed = true;
                }
                args[t] = resolvedTypeArgument;
            }
            return changed ? new ParameterizedTypeImpl(newOwnerType, original.getRawType(), args) : original;
        }
        if (toResolve instanceof WildcardType) {
            Type upperBound;
            original = (WildcardType)toResolve;
            Type[] originalLowerBound = original.getLowerBounds();
            Type[] originalUpperBound = original.getUpperBounds();
            if (originalLowerBound.length == 1) {
                Type lowerBound = Types.resolve(context, contextRawType, originalLowerBound[0]);
                if (lowerBound != originalLowerBound[0]) {
                    return new WildcardTypeImpl(new Type[]{Object.class}, new Type[]{lowerBound});
                }
            } else if (originalUpperBound.length == 1 && (upperBound = Types.resolve(context, contextRawType, originalUpperBound[0])) != originalUpperBound[0]) {
                return new WildcardTypeImpl(new Type[]{upperBound}, EMPTY_TYPE_ARRAY);
            }
            return original;
        }
        return toResolve;
    }

    private static Type resolveTypeVariable(Type context, Class<?> contextRawType, TypeVariable<?> unknown) {
        Class<?> declaredByRaw = Types.declaringClassOf(unknown);
        if (declaredByRaw == null) {
            return unknown;
        }
        Type declaredBy = Types.getGenericSupertype(context, contextRawType, declaredByRaw);
        if (declaredBy instanceof ParameterizedType) {
            int index = Types.indexOf(declaredByRaw.getTypeParameters(), unknown);
            return ((ParameterizedType)declaredBy).getActualTypeArguments()[index];
        }
        return unknown;
    }

    private static Class<?> declaringClassOf(TypeVariable<?> typeVariable) {
        Object genericDeclaration = typeVariable.getGenericDeclaration();
        return genericDeclaration instanceof Class ? (Class)genericDeclaration : null;
    }

    private static void checkNotPrimitive(Type type) {
        if (type instanceof Class && ((Class)type).isPrimitive()) {
            throw new IllegalArgumentException();
        }
    }

    static final class WildcardTypeImpl
    implements WildcardType {
        private final Type upperBound;
        private final Type lowerBound;

        WildcardTypeImpl(Type[] upperBounds, Type[] lowerBounds) {
            if (lowerBounds.length > 1) {
                throw new IllegalArgumentException();
            }
            if (upperBounds.length != 1) {
                throw new IllegalArgumentException();
            }
            if (lowerBounds.length == 1) {
                if (lowerBounds[0] == null) {
                    throw new NullPointerException();
                }
                Types.checkNotPrimitive(lowerBounds[0]);
                if (upperBounds[0] != Object.class) {
                    throw new IllegalArgumentException();
                }
                this.lowerBound = lowerBounds[0];
                this.upperBound = Object.class;
            } else {
                if (upperBounds[0] == null) {
                    throw new NullPointerException();
                }
                Types.checkNotPrimitive(upperBounds[0]);
                this.lowerBound = null;
                this.upperBound = upperBounds[0];
            }
        }

        @Override
        public Type[] getUpperBounds() {
            return new Type[]{this.upperBound};
        }

        @Override
        public Type[] getLowerBounds() {
            Type[] typeArray;
            if (this.lowerBound != null) {
                Type[] typeArray2 = new Type[1];
                typeArray = typeArray2;
                typeArray2[0] = this.lowerBound;
            } else {
                typeArray = EMPTY_TYPE_ARRAY;
            }
            return typeArray;
        }

        public boolean equals(Object other) {
            return other instanceof WildcardType && Types.equals(this, (WildcardType)other);
        }

        public int hashCode() {
            return (this.lowerBound != null ? 31 + this.lowerBound.hashCode() : 1) ^ 31 + this.upperBound.hashCode();
        }

        public String toString() {
            if (this.lowerBound != null) {
                return "? super " + Types.typeToString(this.lowerBound);
            }
            if (this.upperBound == Object.class) {
                return "?";
            }
            return "? extends " + Types.typeToString(this.upperBound);
        }
    }

    private static final class GenericArrayTypeImpl
    implements GenericArrayType {
        private final Type componentType;

        GenericArrayTypeImpl(Type componentType) {
            this.componentType = componentType;
        }

        @Override
        public Type getGenericComponentType() {
            return this.componentType;
        }

        public boolean equals(Object o) {
            return o instanceof GenericArrayType && Types.equals(this, (GenericArrayType)o);
        }

        public int hashCode() {
            return this.componentType.hashCode();
        }

        public String toString() {
            return Types.typeToString(this.componentType) + "[]";
        }
    }

    static final class ParameterizedTypeImpl
    implements ParameterizedType {
        private final Type ownerType;
        private final Type rawType;
        private final Type[] typeArguments;

        ParameterizedTypeImpl(Type ownerType, Type rawType, Type ... typeArguments) {
            if (rawType instanceof Class && ownerType == null != (((Class)rawType).getEnclosingClass() == null)) {
                throw new IllegalArgumentException();
            }
            this.ownerType = ownerType;
            this.rawType = rawType;
            for (Type typeArgument : this.typeArguments = (Type[])typeArguments.clone()) {
                if (typeArgument == null) {
                    throw new NullPointerException();
                }
                Types.checkNotPrimitive(typeArgument);
            }
        }

        @Override
        public Type[] getActualTypeArguments() {
            return (Type[])this.typeArguments.clone();
        }

        @Override
        public Type getRawType() {
            return this.rawType;
        }

        @Override
        public Type getOwnerType() {
            return this.ownerType;
        }

        public boolean equals(Object other) {
            return other instanceof ParameterizedType && Types.equals(this, (ParameterizedType)other);
        }

        public int hashCode() {
            return Arrays.hashCode(this.typeArguments) ^ this.rawType.hashCode() ^ Types.hashCodeOrZero(this.ownerType);
        }

        public String toString() {
            StringBuilder result = new StringBuilder(30 * (this.typeArguments.length + 1));
            result.append(Types.typeToString(this.rawType));
            if (this.typeArguments.length == 0) {
                return result.toString();
            }
            result.append("<").append(Types.typeToString(this.typeArguments[0]));
            for (int i = 1; i < this.typeArguments.length; ++i) {
                result.append(", ").append(Types.typeToString(this.typeArguments[i]));
            }
            return result.append(">").toString();
        }
    }
}

