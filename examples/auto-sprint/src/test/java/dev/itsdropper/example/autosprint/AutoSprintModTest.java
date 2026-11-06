package dev.itsdropper.example.autosprint;

import dev.itsdropper.vantaloader.api.ClassTransformContext;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoSprintModTest {
    @Test
    void injectsMappedSprintChecksIntoLocalPlayerAiStep() throws Exception {
        ClassWriter fixture = new ClassWriter(0);
        fixture.visit(Opcodes.V21, Opcodes.ACC_PUBLIC,
                "net/minecraft/client/player/LocalPlayer", null,
                "net/minecraft/world/entity/LivingEntity", null);
        MethodVisitor method = fixture.visitMethod(Opcodes.ACC_PUBLIC, "aiStep", "()V", null, null);
        method.visitCode();
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        fixture.visitEnd();

        ClassTransformContext context = new ClassTransformContext(
                "auto_sprint", "test-transform", "net.minecraft.client.player.LocalPlayer");
        byte[] transformed = new AutoSprintMod().transformLocalPlayer(context, fixture.toByteArray());

        List<String> calls = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        new ClassReader(transformed).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                if (!"aiStep".equals(name)) return null;
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String methodName,
                                                String methodDescriptor, boolean isInterface) {
                        calls.add(owner + "." + methodName + methodDescriptor);
                    }

                    @Override
                    public void visitFieldInsn(int opcode, String owner, String fieldName, String descriptor) {
                        fields.add(owner + "." + fieldName + descriptor);
                    }
                };
            }
        }, 0);

        assertTrue(calls.contains("net/minecraft/client/KeyMapping.isDown()Z"));
        assertTrue(calls.contains("net/minecraft/world/entity/LivingEntity.isUsingItem()Z"));
        assertTrue(calls.contains("net/minecraft/world/entity/Entity.setSprinting(Z)V"));
        assertTrue(fields.contains("net/minecraft/client/Options.keyUpLnet/minecraft/client/KeyMapping;"));
        assertTrue(fields.contains("net/minecraft/client/Options.keyShiftLnet/minecraft/client/KeyMapping;"));
    }

    @Test
    void rejectsMissingAiStepInsteadOfSilentlyInstallingAnInactiveMod() {
        byte[] fixture = fixture("otherMethod");
        ClassTransformContext context = new ClassTransformContext(
                "auto_sprint", "test-transform", "net.minecraft.client.player.LocalPlayer");

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> new AutoSprintMod().transformLocalPlayer(context, fixture));
    }

    @Test
    void rejectsAmbiguousDuplicateAiStepTargets() {
        byte[] fixture = fixture("aiStep", "aiStep");
        ClassTransformContext context = new ClassTransformContext(
                "auto_sprint", "test-transform", "net.minecraft.client.player.LocalPlayer");

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> new AutoSprintMod().transformLocalPlayer(context, fixture));
    }

    private static byte[] fixture(String... methodNames) {
        ClassWriter fixture = new ClassWriter(0);
        fixture.visit(Opcodes.V21, Opcodes.ACC_PUBLIC,
                "net/minecraft/client/player/LocalPlayer", null,
                "net/minecraft/world/entity/LivingEntity", null);
        for (String methodName : methodNames) {
            MethodVisitor method = fixture.visitMethod(Opcodes.ACC_PUBLIC, methodName, "()V", null, null);
            method.visitCode();
            method.visitInsn(Opcodes.RETURN);
            method.visitMaxs(0, 0);
            method.visitEnd();
        }
        fixture.visitEnd();
        return fixture.toByteArray();
    }
}
