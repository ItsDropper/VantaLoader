package dev.itsdropper.vantaloader.transformation;

import dev.itsdropper.vantaloader.api.ClassTransformContext;
import dev.itsdropper.vantaloader.mapping.MinecraftMappings;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.util.CheckClassAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TransformationServiceTest {
    @TempDir Path temporaryDirectory;

    @Test
    void appliesExactTargetTransformationsInStablePriorityOrder() throws Exception {
        TransformationService service = new TransformationService();
        List<String> order = new ArrayList<>();
        String target = Type.getInternalName(BytecodeFixture.class).replace("/", ".");
        byte[] original = fixtureBytes();

        service.forMod("zeta").register("append-z", target, 10, (context, bytes) -> {
            order.add(context.modId() + ":" + context.transformationId());
            return bytes;
        });
        service.forMod("alpha").register("append-a", target, 10, (context, bytes) -> {
            order.add(context.modId() + ":" + context.transformationId());
            return bytes;
        });
        service.forMod("alpha").register("append-first", target, -5, (context, bytes) -> {
            order.add(context.modId() + ":" + context.transformationId());
            assertEquals(target, context.targetClassName());
            return bytes;
        });

        assertNull(service.transform(null, null, target.replace(".", "/"), null, null, original));
        assertEquals(List.of("alpha:append-first", "alpha:append-a", "zeta:append-z"), order);
    }

    @Test
    void ignoresClassesWithoutAnExactRegisteredTarget() throws Exception {
        TransformationService service = new TransformationService();
        String target = Type.getInternalName(BytecodeFixture.class).replace("/", ".");
        service.forMod("example").register("target", target, 0, (context, bytes) -> bytes);

        String otherClass = Type.getInternalName(OtherBytecodeFixture.class);
        assertNull(service.transform(null, null, otherClass, null, null,
                fixtureBytes(OtherBytecodeFixture.class)));
        String nestedClass = Type.getInternalName(BytecodeFixture.Nested.class);
        assertNull(service.transform(null, null, nestedClass, null, null,
                fixtureBytes(BytecodeFixture.Nested.class)));
    }

    @Test
    void supportsLegacyJavaEightTransformerCallback() throws Exception {
        TransformationService service = new TransformationService();
        String target = Type.getInternalName(BytecodeFixture.class);
        List<String> invoked = new ArrayList<>();
        service.forMod("legacy").register("java8-callback", target.replace("/", "."), 0,
                (context, bytes) -> {
                    invoked.add(context.transformationId());
                    return bytes;
                });

        // This is the five-argument ClassFileTransformer method present on Java 8.
        assertNull(service.transform(getClass().getClassLoader(), target,
                null, null, fixtureBytes()));
        assertEquals(List.of("java8-callback"), invoked);
    }

    @Test
    void rejectsDuplicateIdsWithinOneModButAllowsOtherModsToReuseIds() {
        TransformationService service = new TransformationService();
        var registry = service.forMod("first");
        registry.register("patch", "example.Target", 0, (context, bytes) -> bytes);
        assertThrows(IllegalArgumentException.class,
                () -> registry.register("patch", "example.Other", 0, (context, bytes) -> bytes));
        assertDoesNotThrow(() -> service.forMod("second")
                .register("patch", "example.Other", 0, (context, bytes) -> bytes));
    }

    @Test
    void transformsVerifiesAndLoadsARealClassFileWithChangedBehavior() throws Exception {
        TransformationService service = new TransformationService();
        String internalName = Type.getInternalName(BytecodeFixture.class);
        service.forMod("bytecode").register("replace-constant", internalName.replace("/", "."), 0,
                (context, original) -> {
                    ClassReader reader = new ClassReader(original);
                    ClassWriter writer = new ClassWriter(reader, 0);
                    ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
                        @Override
                        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                         String signature, String[] exceptions) {
                            MethodVisitor delegate = super.visitMethod(access, name, descriptor, signature, exceptions);
                            if (!"value".equals(name) || !"()Ljava/lang/String;".equals(descriptor)) return delegate;
                            return new MethodVisitor(Opcodes.ASM9, delegate) {
                                @Override public void visitLdcInsn(Object value) {
                                    super.visitLdcInsn("before".equals(value) ? "after" : value);
                                }
                            };
                        }
                    };
                    reader.accept(visitor, 0);
                    return writer.toByteArray();
                });

        byte[] transformed = service.transform(null, getClass().getClassLoader(), internalName,
                null, null, fixtureBytes());
        assertNotNull(transformed);

        StringWriter diagnostics = new StringWriter();
        CheckClassAdapter.verify(new ClassReader(transformed), getClass().getClassLoader(), false,
                new PrintWriter(diagnostics));
        assertTrue(diagnostics.toString().isBlank(), diagnostics.toString());

        IsolatedClassLoader isolated = new IsolatedClassLoader(getClass().getClassLoader());
        Class<?> transformedType = isolated.define(internalName.replace("/", "."), transformed);
        Object instance = transformedType.getConstructor().newInstance();
        assertEquals("after", transformedType.getMethod("value").invoke(instance));
    }

    @Test
    void rejectsTransformationsThatChangeTheTargetClassIdentity() throws Exception {
        TransformationService service = new TransformationService();
        String target = Type.getInternalName(BytecodeFixture.class).replace("/", ".");
        service.forMod("invalid").register("wrong-target", target, 0, (context, bytes) -> {
            ClassReader reader = new ClassReader(bytes);
            ClassWriter writer = new ClassWriter(reader, 0);
            reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
                @Override public void visit(int version, int access, String name, String signature,
                                            String superName, String[] interfaces) {
                    super.visit(version, access, "example/Other", signature, superName, interfaces);
                }
            }, 0);
            return writer.toByteArray();
        });

        byte[] result = service.transform(null, null, target.replace(".", "/"), null, null, fixtureBytes());
        assertArrayEquals(new byte[]{0, 0, 0, 0}, result);
    }

    @Test
    void failsClosedWhenATransformerThrowsAnError() throws Exception {
        TransformationService service = new TransformationService();
        String target = Type.getInternalName(BytecodeFixture.class).replace("/", ".");
        service.forMod("broken").register("assertion", target, 0,
                (context, bytes) -> { throw new AssertionError("fixture assertion"); });

        byte[] failureBytes = service.transform(null, null, target.replace(".", "/"), null, null, fixtureBytes());
        assertArrayEquals(new byte[]{0, 0, 0, 0}, failureBytes);
    }

    @Test
    void failsClosedWhenATransformerThrows() throws Exception {
        TransformationService service = new TransformationService();
        String target = Type.getInternalName(BytecodeFixture.class).replace("/", ".");
        service.forMod("broken").register("explode", target, 0,
                (ClassTransformContext context, byte[] bytes) -> {
                    throw new IllegalStateException("fixture failure");
                });

        byte[] failureBytes = service.transform(null, null, target.replace(".", "/"), null, null, fixtureBytes());
        assertArrayEquals(new byte[]{0, 0, 0, 0}, failureBytes);
    }



    @Test
    void composesBytecodeChangesFromMultipleModsOnTheSameTarget() throws Exception {
        TransformationService service = new TransformationService();
        String internalName = Type.getInternalName(BytecodeFixture.class);
        String target = internalName.replace("/", ".");
        service.forMod("first_mod").register("first-change", target, 0,
                (context, bytes) -> replaceStringConstant(bytes, "before", "middle"));
        service.forMod("second_mod").register("second-change", target, 10,
                (context, bytes) -> replaceStringConstant(bytes, "middle", "after"));

        byte[] transformed = service.transform(null, getClass().getClassLoader(), internalName,
                null, null, fixtureBytes());
        assertNotNull(transformed);
        StringWriter diagnostics = new StringWriter();
        CheckClassAdapter.verify(new ClassReader(transformed), getClass().getClassLoader(), false,
                new PrintWriter(diagnostics));
        assertTrue(diagnostics.toString().isBlank(), diagnostics.toString());

        IsolatedClassLoader isolated = new IsolatedClassLoader(getClass().getClassLoader());
        Class<?> type = isolated.define(target, transformed);
        assertEquals("after", type.getMethod("value").invoke(type.getConstructor().newInstance()));
    }

    @Test
    void aLaterTransformationFailureRejectsTheWholeComposition() throws Exception {
        TransformationService service = new TransformationService();
        String internalName = Type.getInternalName(BytecodeFixture.class);
        String target = internalName.replace("/", ".");
        List<String> invoked = new ArrayList<>();
        service.forMod("first_mod").register("first-change", target, 0, (context, bytes) -> {
            invoked.add("first");
            return replaceStringConstant(bytes, "before", "middle");
        });
        service.forMod("broken_mod").register("broken-change", target, 10, (context, bytes) -> {
            invoked.add("broken");
            throw new IllegalStateException("intentional integration failure");
        });
        service.forMod("last_mod").register("must-not-run", target, 20, (context, bytes) -> {
            invoked.add("last");
            return bytes;
        });

        byte[] result = service.transform(null, getClass().getClassLoader(), internalName,
                null, null, fixtureBytes());
        assertArrayEquals(new byte[]{0, 0, 0, 0}, result);
        assertEquals(List.of("first", "broken"), invoked);
    }

    @Test
    void reportsCrossModSharedTargetsInDeterministicContributionOrder() {
        TransformationService service = new TransformationService();
        service.forMod("zeta").register("later", "example.Target", 10, (context, bytes) -> bytes);
        service.forMod("alpha").register("first", "example.Target", -5, (context, bytes) -> bytes);
        service.forMod("alpha").register("same-mod", "example.Target", 0, (context, bytes) -> bytes);
        service.forMod("solo").register("only", "example.Other", 0, (context, bytes) -> bytes);

        var conflicts = service.potentialConflicts();
        assertEquals(1, conflicts.size());
        assertEquals("example.Target", conflicts.get(0).runtimeTargetClassName());
        assertEquals(List.of(
                new dev.itsdropper.vantaloader.transformation.TransformationConflict.Contribution(
                        "alpha", "first", -5),
                new dev.itsdropper.vantaloader.transformation.TransformationConflict.Contribution(
                        "alpha", "same-mod", 0),
                new dev.itsdropper.vantaloader.transformation.TransformationConflict.Contribution(
                        "zeta", "later", 10)), conflicts.get(0).contributions());
        assertThrows(UnsupportedOperationException.class,
                () -> conflicts.get(0).contributions().clear());
    }

    @Test
    void excludesMixinAndLoaderInfrastructureFromMixinTransformation() {
        assertFalse(TransformationService.shouldApplyMixin("org.spongepowered.asm.launch.MixinBootstrap"));
        assertFalse(TransformationService.shouldApplyMixin("java.lang.ClassFormatError"));
        assertFalse(TransformationService.shouldApplyMixin("dev.itsdropper.vantaloader.mixin.VantaMixinService"));
        assertTrue(TransformationService.shouldApplyMixin("net.minecraft.client.Minecraft"));
        assertTrue(TransformationService.shouldApplyMixin("com.example.mod.Target"));
    }

    @Test
    void onlyPassesValidClassFilesToMixin() {
        assertFalse(TransformationService.hasClassFileMagic(new byte[]{0, 0, 0, 0}));
        assertFalse(TransformationService.hasClassFileMagic(new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA}));
        assertTrue(TransformationService.hasClassFileMagic(new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE}));
    }

    @Test
    void onlyBootstrapsMixinForMappedClassesWithResolvableResources() {
        assertFalse(TransformationService.hasClassResource(getClass().getClassLoader(), "missing/runtime/Class"));
        assertTrue(TransformationService.hasClassResource(getClass().getClassLoader(),
                TransformationServiceTest.class.getName().replace('.', '/')));
    }

    private static byte[] replaceStringConstant(byte[] original, String before, String after) {
        ClassReader reader = new ClassReader(original);
        ClassWriter writer = new ClassWriter(reader, 0);
        ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor delegate = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!"value".equals(name) || !"()Ljava/lang/String;".equals(descriptor)) return delegate;
                return new MethodVisitor(Opcodes.ASM9, delegate) {
                    @Override public void visitLdcInsn(Object value) {
                        super.visitLdcInsn(before.equals(value) ? after : value);
                    }
                };
            }
        };
        reader.accept(visitor, 0);
        return writer.toByteArray();
    }

    @Test
    void resolvesNamedMinecraftTargetsToObfuscatedRuntimeClasses() throws Exception {
        Path mappingFile = temporaryDirectory.resolve("client_mappings.txt");
        Files.writeString(mappingFile, "net.minecraft.client.Minecraft -> a:\n");
        MinecraftMappings mappings = MinecraftMappings.read(mappingFile);
        TransformationService service = new TransformationService(mappings);
        List<String> seenTargets = new ArrayList<>();
        service.forMod("mapped").register("minecraft-client", "net.minecraft.client.Minecraft", 0,
                (context, bytes) -> {
                    seenTargets.add(context.targetClassName());
                    return bytes;
                });

        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "a", null, "java/lang/Object", null);
        writer.visitEnd();
        byte[] runtimeClass = writer.toByteArray();

        assertNull(service.transform(null, getClass().getClassLoader(), "a",
                null, null, runtimeClass));
        assertEquals(List.of("net.minecraft.client.Minecraft"), seenTargets);
    }

    @Test
    void hierarchyWriterFindsCommonSuperclassWithoutDefiningClasses() {
        var writer = new dev.itsdropper.vantaloader.api.HierarchyClassWriter(
                0, getClass().getClassLoader());
        assertEquals(Type.getInternalName(HierarchyBase.class),
                writer.commonSuperClass(Type.getInternalName(HierarchyLeft.class),
                        Type.getInternalName(HierarchyRight.class)));
        assertEquals("[L" + Type.getInternalName(HierarchyBase.class) + ";",
                writer.commonSuperClass("[L" + Type.getInternalName(HierarchyLeft.class) + ";",
                        "[L" + Type.getInternalName(HierarchyRight.class) + ";"));
        assertEquals("java/lang/Object",
                writer.commonSuperClass("[I", "[J"));
        assertEquals("[Ljava/lang/Object;",
                writer.commonSuperClass("[[Ljava/lang/String;", "[Ljava/lang/Object;"));
        assertEquals("[Ljava/lang/Object;",
                writer.commonSuperClass("[Ljava/lang/String;", "[Ljava/lang/Integer;"));
        assertEquals(Type.getInternalName(HierarchyMarker.class),
                writer.commonSuperClass(Type.getInternalName(HierarchyMarker.class),
                        Type.getInternalName(HierarchyLeft.class)));
    }

    public interface HierarchyMarker { }
    public static class HierarchyBase implements HierarchyMarker { }
    public static final class HierarchyLeft extends HierarchyBase { }
    public static final class HierarchyRight extends HierarchyBase { }

    private static byte[] fixtureBytes() throws Exception {
        return fixtureBytes(BytecodeFixture.class);
    }

    private static byte[] fixtureBytes(Class<?> fixtureType) throws Exception {
        String internalName = Type.getInternalName(fixtureType);
        try (InputStream input = fixtureType.getResourceAsStream("/" + internalName + ".class")) {
            assertNotNull(input);
            return input.readAllBytes();
        }
    }

    public static final class BytecodeFixture {
        public String value() { return "before"; }
        public static final class Nested { }
    }

    public static final class OtherBytecodeFixture { }

    private static final class IsolatedClassLoader extends ClassLoader {
        private IsolatedClassLoader(ClassLoader parent) { super(parent); }
        private Class<?> define(String name, byte[] bytes) {
            return defineClass(name, bytes, 0, bytes.length);
        }
    }
}
