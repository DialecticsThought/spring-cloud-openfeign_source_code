/*
 * Decompiled with CFR 0.152.
 */
package feign;

import feign.InvocationHandlerFactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

final class DefaultMethodHandler
implements InvocationHandlerFactory.MethodHandler {
    private final MethodHandle unboundHandle;
    private MethodHandle handle;

    public DefaultMethodHandler(Method defaultMethod) {
        Class<?> declaringClass = defaultMethod.getDeclaringClass();
        try {
            MethodHandles.Lookup lookup = this.readLookup(declaringClass);
            this.unboundHandle = lookup.unreflectSpecial(defaultMethod, declaringClass);
        }
        catch (IllegalAccessException | IllegalArgumentException | NoSuchFieldException | InvocationTargetException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private MethodHandles.Lookup readLookup(Class<?> declaringClass) throws IllegalAccessException, InvocationTargetException, NoSuchFieldException {
        try {
            return this.safeReadLookup(declaringClass);
        }
        catch (NoSuchMethodException e) {
            try {
                return this.androidLookup(declaringClass);
            }
            catch (InstantiationException | NoSuchMethodException instantiationException) {
                return this.legacyReadLookup();
            }
        }
    }

    public MethodHandles.Lookup androidLookup(Class<?> declaringClass) throws InstantiationException, InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        MethodHandles.Lookup lookup;
        try {
            Class<Class> classReference = Class.class;
            Class[] classType = new Class[]{Class.class};
            Method reflectedGetDeclaredConstructor = classReference.getDeclaredMethod("getDeclaredConstructor", Class[].class);
            reflectedGetDeclaredConstructor.setAccessible(true);
            Constructor someHiddenMethod = (Constructor)reflectedGetDeclaredConstructor.invoke(MethodHandles.Lookup.class, new Object[]{classType});
            lookup = (MethodHandles.Lookup)someHiddenMethod.newInstance(declaringClass);
        }
        catch (IllegalAccessException ex0) {
            Constructor lookupConstructor = MethodHandles.Lookup.class.getDeclaredConstructor(Class.class);
            lookupConstructor.setAccessible(true);
            lookup = (MethodHandles.Lookup)lookupConstructor.newInstance(declaringClass);
        }
        return lookup;
    }

    private MethodHandles.Lookup safeReadLookup(Class<?> declaringClass) throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        Object privateLookupIn = MethodHandles.class.getMethod("privateLookupIn", Class.class, MethodHandles.Lookup.class).invoke(null, declaringClass, lookup);
        return (MethodHandles.Lookup)privateLookupIn;
    }

    private MethodHandles.Lookup legacyReadLookup() throws NoSuchFieldException, IllegalAccessException {
        Field field = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
        field.setAccessible(true);
        MethodHandles.Lookup lookup = (MethodHandles.Lookup)field.get(null);
        return lookup;
    }

    public void bindTo(Object proxy) {
        if (this.handle != null) {
            throw new IllegalStateException("Attempted to rebind a default method handler that was already bound");
        }
        this.handle = this.unboundHandle.bindTo(proxy);
    }

    @Override
    public Object invoke(Object[] argv) throws Throwable {
        if (this.handle == null) {
            throw new IllegalStateException("Default method handler invoked before proxy has been bound.");
        }
        return this.handle.invokeWithArguments(argv);
    }
}

