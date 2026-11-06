package dev.itsdropper.vantaloader.bootstrap.minecraft;

import dev.itsdropper.vantaloader.api.ClassTransformContext;
import dev.itsdropper.vantaloader.bootstrap.MinecraftHooks;
import dev.itsdropper.vantaloader.transformation.TransformationService;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.objectweb.asm.tree.analysis.Frame;
import java.util.Objects;

/** Initial exact-version client adapter for Minecraft 1.21.11. */
public final class Minecraft12111Adapter implements MinecraftAdapter {
    public static final String VERSION = "1.21.11";
    public static final String CLIENT_CLASS = "net.minecraft.client.Minecraft";
    private static final String CLIENT_TICK_METHOD = "tick";
    private static final String CLIENT_TICK_DESCRIPTOR = "()V";
    private static final String HOOK_OWNER = Type.getInternalName(MinecraftHooks.class);
    private static final String TICK_HOOK_DESCRIPTOR = "(Ljava/lang/Object;Z)V";
    private static final String MENU_HOOK_OWNER = "dev/itsdropper/vantaloader/mixin/client/VantaMenuHooks";
    private static final String MENU_HOOK_DESCRIPTOR = "(Ljava/lang/Object;)V";

    @Override public String minecraftVersion() { return VERSION; }

    @Override
    public void install(TransformationService transformations) {
        var registry = Objects.requireNonNull(transformations, "transformations").forMod("vantaloader");
        registry.register("minecraft-1.21.11-client-tick", CLIENT_CLASS, 0, this::transformClientTick);
        registry.register("minecraft-1.21.11-title-screen", "net.minecraft.client.gui.screens.TitleScreen",
                0, (context, bytes) -> transformScreenInit(context, bytes, "title"));
        registry.register("minecraft-1.21.11-title-footer", "net.minecraft.client.gui.screens.TitleScreen",
                1, this::transformTitleRender);
        registry.register("minecraft-1.21.11-pause-screen", "net.minecraft.client.gui.screens.PauseScreen",
                0, (context, bytes) -> transformScreenInit(context, bytes, "pause"));
    }

    private byte[] transformScreenInit(ClassTransformContext context, byte[] originalClass, String screenKind) {
        String owner = screenKind.equals("title") ? "net.minecraft.client.gui.screens.TitleScreen"
                : "net.minecraft.client.gui.screens.PauseScreen";
        String runtimeMethod = context.mappings().runtimeMethodName(owner, "init", "()V");
        String runtimeDescriptor = context.mappings().runtimeDescriptor("()V");
        ClassReader reader = new ClassReader(originalClass);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        boolean[] matched = {false};
        ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor delegate = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!runtimeMethod.equals(name) || !runtimeDescriptor.equals(descriptor)) return delegate;
                if (matched[0]) throw new IllegalStateException("Multiple matching screen init methods found");
                matched[0] = true;
                return new MethodVisitor(Opcodes.ASM9, delegate) {
                    @Override
                    public void visitInsn(int opcode) {
                        if (opcode == Opcodes.RETURN) {
                            // This hook only consumes the existing receiver. Avoid LocalVariablesSorter:
                            // Minecraft's obfuscated methods contain complex StackMapTable chop frames.
                            super.visitVarInsn(Opcodes.ALOAD, 0);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, MENU_HOOK_OWNER,
                                    "addMenuButton", MENU_HOOK_DESCRIPTOR, false);
                        }
                        super.visitInsn(opcode);
                    }
                };
            }
        };
        reader.accept(visitor, 0);
        if (!matched[0]) {
            throw new IllegalStateException("Expected Minecraft 1.21.11 " + screenKind
                    + " screen init method missing: " + runtimeMethod + runtimeDescriptor);
        }
        return writer.toByteArray();
    }

    private byte[] transformTitleRender(ClassTransformContext context, byte[] originalClass) {
        String owner = "net.minecraft.client.gui.screens.TitleScreen";
        String descriptor = "(Lnet/minecraft/client/gui/GuiGraphics;IIF)V";
        String runtimeMethod = context.mappings().runtimeMethodName(owner, "render", descriptor);
        String runtimeDescriptor = context.mappings().runtimeDescriptor(descriptor);
        ClassNode classNode = new ClassNode(Opcodes.ASM9);
        new ClassReader(originalClass).accept(classNode, ClassReader.EXPAND_FRAMES);
        MethodNode target = null;
        for (MethodNode method : classNode.methods) {
            if (!runtimeMethod.equals(method.name) || !runtimeDescriptor.equals(method.desc)) continue;
            if (target != null) throw new IllegalStateException("Multiple title render methods found");
            target = method;
        }
        if (target == null) throw new IllegalStateException("Minecraft 1.21.11 title render method missing");
        try {
            Frame<?>[] frames = new Analyzer<>(new BasicInterpreter()).analyze(classNode.name, target);
            int injected = 0;
            for (int i = 0; i < target.instructions.size(); i++) {
                AbstractInsnNode instruction = target.instructions.get(i);
                if (instruction.getOpcode() != Opcodes.RETURN) continue;
                Frame<?> frame = frames[i];
                if (frame == null || frame.getLocals() < 2
                        || frame.getLocal(0) == null || frame.getLocal(1) == null) continue;
                InsnList hook = new InsnList();
                hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
                hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
                hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, MENU_HOOK_OWNER,
                        "renderTitleFooter", "(Ljava/lang/Object;Ljava/lang/Object;)V", false));
                target.instructions.insertBefore(instruction, hook);
                injected++;
            }
            if (injected == 0) throw new IllegalStateException("No reachable title render return accepts footer hook");
        } catch (org.objectweb.asm.tree.analysis.AnalyzerException failure) {
            throw new IllegalStateException("Could not analyze Minecraft title render method", failure);
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classNode.accept(writer);
        return writer.toByteArray();
    }

    private byte[] transformClientTick(ClassTransformContext context, byte[] originalClass) {
        String runtimeMethod = context.mappings().runtimeMethodName(
                CLIENT_CLASS, CLIENT_TICK_METHOD, CLIENT_TICK_DESCRIPTOR);
        String runtimeDescriptor = context.mappings().runtimeDescriptor(CLIENT_TICK_DESCRIPTOR);
        ClassReader reader = new ClassReader(originalClass);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        boolean[] matched = {false};

        ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor delegate = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!runtimeMethod.equals(name) || !runtimeDescriptor.equals(descriptor)) return delegate;
                if (matched[0]) throw new IllegalStateException("Multiple matching client tick methods found");
                matched[0] = true;
                return new MethodVisitor(Opcodes.ASM9, delegate) {
                    @Override
                    public void visitCode() {
                        super.visitCode();
                        super.visitVarInsn(Opcodes.ALOAD, 0);
                        super.visitInsn(Opcodes.ICONST_1);
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOK_OWNER,
                                "onClientTick", TICK_HOOK_DESCRIPTOR, false);
                    }

                };
            }
        };
        // The entry hook uses only the receiver and operand stack. Do not inject at
        // RETURN: valid Minecraft control-flow frames can chop local 0 before a
        // return instruction, making an injected ALOAD 0 unverifiable.
        reader.accept(visitor, 0);
        if (!matched[0]) {
            throw new IllegalStateException("Expected Minecraft 1.21.11 target missing: "
                    + CLIENT_CLASS + "." + runtimeMethod + runtimeDescriptor);
        }
        return writer.toByteArray();
    }
}
