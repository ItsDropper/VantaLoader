package dev.itsdropper.vantaloader.api;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ClassWriter that computes common supertypes from class-file headers without
 * defining or initializing game classes in the JVM.
 *
 * Pass the Minecraft/game class loader so class resources are resolved from
 * the same runtime class path as the transformed target.
 */
public final class HierarchyClassWriter extends ClassWriter {
    private final ClassLoader loader;
    private final ConcurrentHashMap<String, ClassInfo> classes = new ConcurrentHashMap<>();

    public HierarchyClassWriter(ClassReader reader, int flags, ClassLoader loader) {
        super(reader, flags);
        this.loader = loader;
    }

    public HierarchyClassWriter(int flags, ClassLoader loader) {
        super(flags);
        this.loader = loader;
    }

    public String commonSuperClass(String first, String second) {
        return getCommonSuperClass(first, second);
    }

    @Override
    protected String getCommonSuperClass(String first, String second) {
        if (first.equals(second)) return first;
        if (first.startsWith("[") || second.startsWith("[")) {
            return commonArraySupertype(first, second);
        }
        if (isAssignableFrom(first, second)) return first;
        if (isAssignableFrom(second, first)) return second;

        ClassInfo firstInfo = info(first);
        ClassInfo secondInfo = info(second);
        if (firstInfo == null || secondInfo == null
                || firstInfo.isInterface() || secondInfo.isInterface()) {
            return "java/lang/Object";
        }

        String candidate = firstInfo.superName();
        while (candidate != null) {
            if (isAssignableFrom(candidate, second)) return candidate;
            ClassInfo candidateInfo = info(candidate);
            candidate = candidateInfo == null ? null : candidateInfo.superName();
        }
        return "java/lang/Object";
    }

    private String commonArraySupertype(String first, String second) {
        if (!first.startsWith("[") || !second.startsWith("[")) {
            if ("java/lang/Object".equals(first) || "java/lang/Cloneable".equals(first)
                    || "java/io/Serializable".equals(first)) return first;
            if ("java/lang/Object".equals(second) || "java/lang/Cloneable".equals(second)
                    || "java/io/Serializable".equals(second)) return second;
            return "java/lang/Object";
        }

        String firstComponent = first.substring(1);
        String secondComponent = second.substring(1);
        if (firstComponent.equals(secondComponent)) return first;
        if (isPrimitiveDescriptor(firstComponent) || isPrimitiveDescriptor(secondComponent)) {
            return "java/lang/Object";
        }

        String commonComponent;
        if (firstComponent.startsWith("[") && secondComponent.startsWith("[")) {
            commonComponent = commonArraySupertype(firstComponent, secondComponent);
            if (!commonComponent.startsWith("[")) return "[L" + commonComponent + ";";
            return "[" + commonComponent;
        } else if (firstComponent.startsWith("[") || secondComponent.startsWith("[")) {
            commonComponent = "java/lang/Object";
        } else {
            String firstName = firstComponent.substring(1, firstComponent.length() - 1);
            String secondName = secondComponent.substring(1, secondComponent.length() - 1);
            commonComponent = getCommonSuperClass(firstName, secondName);
        }
        return "[L" + commonComponent + ";";
    }

    private static boolean isPrimitiveDescriptor(String descriptor) {
        return descriptor.length() == 1 && "ZCBSIFJD".indexOf(descriptor.charAt(0)) >= 0;
    }

    private boolean isAssignableFrom(String target, String source) {
        if (target.equals(source) || "java/lang/Object".equals(target)) return true;
        ClassInfo sourceInfo = info(source);
        if (sourceInfo == null) return false;

        Set<String> visited = new HashSet<>();
        return hasAncestor(sourceInfo, target, visited);
    }

    private boolean hasAncestor(ClassInfo current, String target, Set<String> visited) {
        if (!visited.add(current.name())) return false;
        if (target.equals(current.superName())) return true;
        for (String interfaceName : current.interfaces()) {
            if (target.equals(interfaceName)) return true;
            ClassInfo interfaceInfo = info(interfaceName);
            if (interfaceInfo != null && hasAncestor(interfaceInfo, target, visited)) return true;
        }
        if (current.superName() == null) return false;
        ClassInfo parent = info(current.superName());
        return parent != null && hasAncestor(parent, target, visited);
    }

    private ClassInfo info(String internalName) {
        if (internalName == null || internalName.startsWith("[")) return null;
        return classes.computeIfAbsent(internalName, this::readInfo);
    }

    private ClassInfo readInfo(String internalName) {
        String resource = internalName + ".class";
        try (InputStream input = loader == null
                ? ClassLoader.getSystemResourceAsStream(resource)
                : loader.getResourceAsStream(resource)) {
            if (input == null) return null;
            ClassReader reader = new ClassReader(input);
            return new ClassInfo(reader.getClassName(), reader.getSuperName(),
                    reader.getInterfaces(), (reader.getAccess() & org.objectweb.asm.Opcodes.ACC_INTERFACE) != 0);
        } catch (IOException | RuntimeException failure) {
            return null;
        }
    }

    private record ClassInfo(String name, String superName, String[] interfaces, boolean isInterface) { }
}
