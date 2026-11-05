package dev.itsdropper.vantaloader.api.accessor;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.beans.Introspector;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Creates typed accessor views over existing game objects.
 *
 * Accessors use MethodHandles rather than injecting mod-owned interfaces into
 * Minecraft classes, which avoids class-loader visibility problems in a
 * Java-agent environment. Private access is subject to the JVM module's
 * normal openness rules.
 */
public final class AccessorFactory {
    private AccessorFactory() { }

    public static <T> T create(Class<T> accessorType, Object target) {
        Objects.requireNonNull(accessorType, "accessorType");
        Objects.requireNonNull(target, "target");
        if (!accessorType.isInterface()) {
            throw new IllegalArgumentException("Accessor type must be an interface: " + accessorType.getName());
        }
        List<Binding> bindings = new ArrayList<>();
        for (Method method : accessorType.getMethods()) {
            if (method.getDeclaringClass() == Object.class || method.isDefault()) continue;
            Accessor accessor = method.getAnnotation(Accessor.class);
            Invoker invoker = method.getAnnotation(Invoker.class);
            if (accessor != null && invoker != null) {
                throw new IllegalArgumentException(method + " cannot declare both @Accessor and @Invoker");
            }
            if (accessor == null && invoker == null) {
                throw new IllegalArgumentException("Accessor method must declare @Accessor or @Invoker: " + method);
            }
            bindings.add(new Binding(method, accessor != null
                    ? resolveField(target.getClass(), method, accessor.value())
                    : resolveMethod(target.getClass(), method, invoker.value())));
        }

        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> "AccessorView[" + accessorType.getName() + " -> "
                            + target.getClass().getName() + "]";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.toString());
                };
            }
            if (method.isDefault()) return InvocationHandler.invokeDefault(proxy, method, args);
            for (Binding binding : bindings) {
                if (binding.method().equals(method)) {
                    return binding.handle().invokeWithArguments(args == null ? List.of() : Arrays.asList(args));
                }
            }
            throw new IllegalStateException("No accessor binding for " + method);
        };
        Object proxy = Proxy.newProxyInstance(accessorType.getClassLoader(),
                new Class<?>[]{accessorType}, handler);
        return accessorType.cast(proxy);
    }

    private static MethodHandle resolveField(Class<?> targetType, Method accessor, String explicitName) {
        String fieldName = explicitName.isBlank() ? inferFieldName(accessor) : explicitName;
        boolean getter = accessor.getParameterCount() == 0 && accessor.getReturnType() != void.class;
        boolean setter = accessor.getParameterCount() == 1 && accessor.getReturnType() == void.class;
        if (!getter && !setter) {
            throw new IllegalArgumentException("@Accessor must be a no-argument getter or one-argument void setter: "
                    + accessor);
        }
        for (Class<?> owner = targetType; owner != null; owner = owner.getSuperclass()) {
            try {
                MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(owner, MethodHandles.lookup());
                if (getter) return lookup.findGetter(owner, fieldName, accessor.getReturnType());
                return lookup.findSetter(owner, fieldName, accessor.getParameterTypes()[0]);
            } catch (NoSuchFieldException ignored) {
                // Continue into the superclass.
            } catch (IllegalAccessException exception) {
                throw inaccessible("field", owner, fieldName, exception);
            } catch (NoSuchMethodException exception) {
                throw new IllegalArgumentException("Field '" + fieldName
                        + "' has a different type than accessor " + accessor, exception);
            }
        }
        throw new IllegalArgumentException("No field '" + fieldName + "' found on " + targetType.getName());
    }

    private static MethodHandle resolveMethod(Class<?> targetType, Method accessor, String explicitName) {
        String methodName = explicitName.isBlank() ? inferInvokedMethodName(accessor) : explicitName;
        MethodType type = MethodType.methodType(accessor.getReturnType(), accessor.getParameterTypes());
        for (Class<?> owner = targetType; owner != null; owner = owner.getSuperclass()) {
            try {
                MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(owner, MethodHandles.lookup());
                return lookup.findVirtual(owner, methodName, type);
            } catch (NoSuchMethodException ignored) {
                // Continue into the superclass.
            } catch (IllegalAccessException exception) {
                throw inaccessible("method", owner, methodName, exception);
            }
        }
        throw new IllegalArgumentException("No instance method '" + methodName + type
                + "' found on " + targetType.getName());
    }

    private static String inferFieldName(Method method) {
        String name = method.getName();
        String property;
        if (name.startsWith("get") && name.length() > 3) property = name.substring(3);
        else if (name.startsWith("is") && name.length() > 2
                && (method.getReturnType() == boolean.class || method.getReturnType() == Boolean.class)) {
            property = name.substring(2);
        } else if (name.startsWith("set") && name.length() > 3) property = name.substring(3);
        else throw new IllegalArgumentException("Cannot infer field name from accessor method " + method);
        return Introspector.decapitalize(property);
    }

    private static String inferInvokedMethodName(Method method) {
        String name = method.getName();
        if (!name.startsWith("invoke") || name.length() <= 6) {
            throw new IllegalArgumentException("Cannot infer target method name from invoker " + method);
        }
        return Introspector.decapitalize(name.substring(6));
    }

    private static IllegalArgumentException inaccessible(String kind, Class<?> owner,
                                                          String name, IllegalAccessException cause) {
        return new IllegalArgumentException("Cannot access " + kind + " '" + name + "' on "
                + owner.getName() + "; the target package may not be open to VantaLoader", cause);
    }

    private record Binding(Method method, MethodHandle handle) { }
}
