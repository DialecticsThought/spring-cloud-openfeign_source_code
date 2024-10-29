/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  org.jvnet.animal_sniffer.IgnoreJRERequirement
 */
package feign;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.jvnet.animal_sniffer.IgnoreJRERequirement;

@IgnoreJRERequirement
final class DefaultMethodHandler
implements InvocationHandlerFactory.MethodHandler {
    private final MethodHandle unboundHandle;
    private MethodHandle handle;

    public DefaultMethodHandler(Method defaultMethod) {
        try {
            Class<?> declaringClass = defaultMethod.getDeclaringClass();
            Field field = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
            field.setAccessible(true);
            MethodHandles.Lookup lookup = (MethodHandles.Lookup)field.get(null);
            this.unboundHandle = lookup.unreflectSpecial(defaultMethod, declaringClass);
        }
        catch (NoSuchFieldException ex) {
            throw new IllegalStateException(ex);
        }
        catch (IllegalAccessException ex) {
            throw new IllegalStateException(ex);
        }
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

