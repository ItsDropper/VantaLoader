package dev.itsdropper.vantaloader.transformation;

import dev.itsdropper.vantaloader.api.ClassTransformContext;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.util.CheckClassAdapter;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TransformationServiceTest {
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

        assertNull(service.transform(null, null, "example/Other", null, null, fixtureBytes()));
        assertNull(service.transform(null, null, "example/BytecodeFixture$Nested", null, null, fixtureBytes()));
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

    private static byte[] fixtureBytes() throws Exception {
        String internalName = Type.getInternalName(BytecodeFixture.class);
        try (InputStream input = BytecodeFixture.class.getResourceAsStream("/" + internalName + ".class")) {
            assertNotNull(input);
            return input.readAllBytes();
        }
    }

    public static final class BytecodeFixture {
        public String value() { return "before"; }
    }

    private static final class IsolatedClassLoader extends ClassLoader {
        private IsolatedClassLoader(ClassLoader parent) { super(parent); }
        private Class<?> define(String name, byte[] bytes) {
            return defineClass(name, bytes, 0, bytes.length);
        }
    }
}
