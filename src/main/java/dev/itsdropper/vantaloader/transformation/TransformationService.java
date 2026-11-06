package dev.itsdropper.vantaloader.transformation;

import dev.itsdropper.vantaloader.api.ClassTransformContext;
import dev.itsdropper.vantaloader.api.ClassTransformer;
import dev.itsdropper.vantaloader.api.TransformationRegistry;
import dev.itsdropper.vantaloader.mapping.MinecraftMappings;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * JVM instrumentation backend for native VantaLoader transformations.
 * Registrations are immutable snapshots during each class definition.
 */
public final class TransformationService implements ClassFileTransformer {
    private static final Logger LOGGER = Logger.getLogger("VantaLoader.Transform");
    private static final Comparator<Registration> ORDER = Comparator
            .comparingInt(Registration::priority)
            .thenComparing(Registration::modId)
            .thenComparing(Registration::id);

    private final CopyOnWriteArrayList<Registration> registrations = new CopyOnWriteArrayList<>();
    private final MinecraftMappings mappings;

    public TransformationService() {
        this(null);
    }

    public TransformationService(MinecraftMappings mappings) {
        this.mappings = mappings;
    }

    public TransformationRegistry forMod(String modId) {
        requireIdentifier(modId, "mod ID");
        return (id, targetClassName, priority, transformer) ->
                register(modId, id, targetClassName, priority, transformer);
    }

    private void register(String modId, String id, String targetClassName,
                          int priority, ClassTransformer transformer) {
        requireIdentifier(id, "transformation ID");
        Objects.requireNonNull(transformer, "transformer");
        if (targetClassName == null || targetClassName.isBlank()
                || targetClassName.indexOf('/') >= 0 || targetClassName.startsWith("[")) {
            throw new IllegalArgumentException("Target must be an exact binary class name");
        }
        if (!targetClassName.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*")) {
            throw new IllegalArgumentException("Invalid binary class name: " + targetClassName);
        }

        Registration registration = new Registration(modId, id, targetClassName, priority, transformer);
        synchronized (registrations) {
            boolean duplicate = registrations.stream().anyMatch(existing ->
                    existing.modId().equals(modId) && existing.id().equals(id));
            if (duplicate) {
                throw new IllegalArgumentException("Duplicate transformation ID '" + modId + ":" + id + "'");
            }
            registrations.add(registration);
            registrations.sort(ORDER);
        }
        LOGGER.info(() -> "Registered transformation " + modId + ":" + id
                + " -> " + targetClassName + " (priority " + priority + ")");
    }

    /**
     * Java 8 Instrumentation entry point. Keep this overload even when compiling
     * against a newer JDK so the backend has the legacy JVM contract available
     * when the project is eventually rebuilt against a Java 8 API baseline.
     */
    @Override
    public byte[] transform(ClassLoader loader, String className,
                            Class<?> classBeingRedefined, ProtectionDomain protectionDomain,
                            byte[] classfileBuffer) throws IllegalClassFormatException {
        return transformInternal(loader, className, classBeingRedefined, protectionDomain, classfileBuffer);
    }

    /** Java 9+ entry point; both JVM contracts share exactly the same pipeline. */
    @Override
    public byte[] transform(Module module, ClassLoader loader, String className,
                            Class<?> classBeingRedefined, ProtectionDomain protectionDomain,
                            byte[] classfileBuffer) throws IllegalClassFormatException {
        return transformInternal(loader, className, classBeingRedefined, protectionDomain, classfileBuffer);
    }

    private byte[] transformInternal(ClassLoader loader, String className,
                                     Class<?> classBeingRedefined, ProtectionDomain protectionDomain,
                                     byte[] classfileBuffer) throws IllegalClassFormatException {
        if (className == null || classfileBuffer == null) return null;
        String binaryName = className.replace('/', '.');
        List<Registration> matching = registrations.stream()
                .filter(registration -> runtimeTargetName(registration).equals(binaryName))
                .toList();
        boolean mixinActive = dev.itsdropper.vantaloader.mixin.VantaMixinService.isInitialized();
        if (matching.isEmpty() && !mixinActive) return null;

        byte[] current = classfileBuffer.clone();
        boolean changed = false;
        if (mixinActive) {
            try {
                byte[] mixinTransformed = dev.itsdropper.vantaloader.mixin.VantaMixinService
                        .transform(binaryName, current);
                if (mixinTransformed == null || mixinTransformed.length == 0) {
                    throw new IllegalStateException("Sponge Mixin returned an empty class for " + binaryName);
                }
                boolean mixinChanged = !java.util.Arrays.equals(current, mixinTransformed);
                if (mappings != null
                        && "net.minecraft.client.gui.screens.TitleScreen".equals(
                                mappings.toNamedClassName(binaryName))) {
                    LOGGER.info("Sponge Mixin processed Minecraft TitleScreen; bytecodeChanged=" + mixinChanged);
                }
                if (mixinChanged) changed = true;
                current = mixinTransformed.clone();
            } catch (Throwable failure) {
                if (failure instanceof VirtualMachineError fatal) throw fatal;
                if (failure instanceof ThreadDeath death) throw death;
                LOGGER.log(Level.SEVERE, "Sponge Mixin transformation failed for " + binaryName, failure);
                return new byte[]{0, 0, 0, 0};
            }
        }
        if (matching.isEmpty()) return changed ? current : null;
        for (Registration registration : matching) {
            try {
                byte[] transformed = registration.transformer().transform(
                        mappings == null
                                ? new ClassTransformContext(registration.modId(), registration.id(), registration.targetClassName())
                                : new ClassTransformContext(registration.modId(), registration.id(), registration.targetClassName(), mappings),
                        current.clone());
                if (transformed == null || transformed.length == 0) {
                    throw new IllegalStateException("Transformer returned an empty or null class file");
                }
                validateClassFile(transformed, binaryName);
                if (!java.util.Arrays.equals(current, transformed)) changed = true;
                current = transformed.clone();
            } catch (Throwable failure) {
                // Do not convert VM-fatal conditions into an ordinary mod failure.
                if (failure instanceof VirtualMachineError fatal) throw fatal;
                if (failure instanceof ThreadDeath death) throw death;
                LOGGER.log(Level.SEVERE, "Transformation failed: " + registration.modId()
                        + ":" + registration.id() + " targeting " + binaryName, failure);
                // Instrumentation normally treats a thrown transformer exception as
                // "no transformation" and continues with the original class bytes. Return
                // an invalid class file instead so a broken required patch cannot silently
                // launch an unpatched game. This also covers AssertionError and LinkageError
                // from third-party callbacks without swallowing VM-fatal failures.
                return new byte[]{0, 0, 0, 0};
            }
        }
        return changed ? current : null;
    }

    private String runtimeTargetName(Registration registration) {
        return mappings == null ? registration.targetClassName()
                : mappings.toRuntimeClassName(registration.targetClassName());
    }

    private static void validateClassFile(byte[] bytes, String expectedBinaryName) {
        ClassReader reader = new ClassReader(bytes);
        String actualBinaryName = reader.getClassName().replace('/', '.');
        if (!expectedBinaryName.equals(actualBinaryName)) {
            throw new IllegalArgumentException("Transformer changed target class identity from "
                    + expectedBinaryName + " to " + actualBinaryName);
        }
        // Traverse the complete class structure and code attributes. This catches
        // malformed/truncated class files, but is not a substitute for JVM type
        // verification or hierarchy-aware stack-map-frame analysis.
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public org.objectweb.asm.MethodVisitor visitMethod(int access, String name,
                    String descriptor, String signature, String[] exceptions) {
                return new org.objectweb.asm.MethodVisitor(Opcodes.ASM9) { };
            }
        }, 0);
    }

    public int registrationCount() {
        return registrations.size();
    }

    private static void requireIdentifier(String value, String label) {
        if (value == null || !value.matches("[a-z0-9][a-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException("Invalid " + label + ": " + value);
        }
    }

    private record Registration(String modId, String id, String targetClassName,
                                int priority, ClassTransformer transformer) { }
}
