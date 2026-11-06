package dev.itsdropper.vantaloader.mapping;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class MinecraftClassRemapperTest {
    @TempDir Path temporaryDirectory;

    @Test
    void remapsMinecraftTypesDescriptorsAndMemberReferences() throws Exception {
        Path file = temporaryDirectory.resolve("client_mappings.txt");
        Files.writeString(file, """
                net.minecraft.network.chat.Component -> ab:
                    net.minecraft.network.chat.Component literal(java.lang.String) -> a
                """);
        MinecraftMappings mappings = MinecraftMappings.read(file);

        ClassWriter source = new ClassWriter(0);
        source.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "test/UsesComponent", null,
                "java/lang/Object", null);
        source.visitField(Opcodes.ACC_PRIVATE, "component",
                "Lnet/minecraft/network/chat/Component;", null, null).visitEnd();
        MethodVisitor method = source.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "create", "(Ljava/lang/String;)Lnet/minecraft/network/chat/Component;", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "net/minecraft/network/chat/Component",
                "literal", "(Ljava/lang/String;)Lnet/minecraft/network/chat/Component;", false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(1, 1);
        method.visitEnd();
        source.visitEnd();

        byte[] remapped = MinecraftClassRemapper.remap(source.toByteArray(), mappings);
        AtomicReference<String> fieldDescriptor = new AtomicReference<>();
        AtomicReference<String> methodDescriptor = new AtomicReference<>();
        AtomicReference<String> invocation = new AtomicReference<>();
        new ClassReader(remapped).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public org.objectweb.asm.FieldVisitor visitField(int access, String name, String descriptor,
                                                               String signature, Object value) {
                fieldDescriptor.set(descriptor);
                return null;
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                methodDescriptor.set(descriptor);
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name,
                                                String descriptor, boolean isInterface) {
                        invocation.set(owner + "#" + name + descriptor);
                    }
                };
            }
        }, 0);

        assertEquals("test/UsesComponent", new ClassReader(remapped).getClassName());
        assertEquals("Lab;", fieldDescriptor.get());
        assertEquals("(Ljava/lang/String;)Lab;", methodDescriptor.get());
        assertEquals("ab#a(Ljava/lang/String;)Lab;", invocation.get());
    }
}
