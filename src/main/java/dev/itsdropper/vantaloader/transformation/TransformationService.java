package dev.itsdropper.vantaloader.transformation;

import dev.itsdropper.vantaloader.api.ClassTransformContext;
import dev.itsdropper.vantaloader.api.ClassTransformer;
import dev.itsdropper.vantaloader.api.TransformationRegistry;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.ArrayList;
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
            List<Registration> sorted = new ArrayList<>(registrations);
            sorted.sort(ORDER);
            registrations.clear();
            registrations.addAll(sorted);
        }
        LOGGER.info(() -> "Registered transformation " + modId + ":" + id
                + " -> " + targetClassName + " (priority " + priority + ")");
    }

    @Override
    public byte[] transform(Module module, ClassLoader loader, String className,
                            Class<?> classBeingRedefined, ProtectionDomain protectionDomain,
                            byte[] classfileBuffer) throws IllegalClassFormatException {
        if (className == null || classfileBuffer == null) return null;
        String binaryName = className.replace('/', '.');
        List<Registration> matching = registrations.stream()
                .filter(registration -> registration.targetClassName().equals(binaryName))
                .toList();
        if (matching.isEmpty()) return null;

        byte[] current = classfileBuffer.clone();
        boolean changed = false;
        for (Registration registration : matching) {
            try {
                byte[] transformed = registration.transformer().transform(
                        new ClassTransformContext(registration.modId(), registration.id(), binaryName),
                        current.clone());
                if (transformed == null || transformed.length == 0) {
                    throw new IllegalStateException("Transformer returned an empty or null class file");
                }
                if (!java.util.Arrays.equals(current, transformed)) changed = true;
                current = transformed.clone();
            } catch (Exception failure) {
                LOGGER.log(Level.SEVERE, "Transformation failed: " + registration.modId()
                        + ":" + registration.id() + " targeting " + binaryName, failure);
                IllegalClassFormatException formatFailure = new IllegalClassFormatException(
                        "VantaLoader transformation " + registration.modId() + ":" + registration.id()
                                + " failed for " + binaryName + ": " + failure.getMessage());
                formatFailure.initCause(failure);
                throw formatFailure;
            }
        }
        return changed ? current : null;
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
