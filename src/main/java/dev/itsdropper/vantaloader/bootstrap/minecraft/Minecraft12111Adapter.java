package dev.itsdropper.vantaloader.bootstrap.minecraft;

import dev.itsdropper.vantaloader.api.ClassTransformContext;
import dev.itsdropper.vantaloader.api.HierarchyClassWriter;
import dev.itsdropper.vantaloader.bootstrap.MinecraftHooks;
import dev.itsdropper.vantaloader.transformation.TransformationService;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import java.util.Objects;

/** Initial exact-version client adapter for Minecraft 1.21.11. */
public final class Minecraft12111Adapter implements MinecraftAdapter {
    public static final String VERSION = "1.21.11";
    public static final String CLIENT_CLASS = "net.minecraft.client.Minecraft";
    public static final String KEYBOARD_HANDLER_CLASS = "net.minecraft.client.KeyboardHandler";
    public static final String CHAT_SCREEN_CLASS = "net.minecraft.client.gui.screens.ChatScreen";
    public static final String HUD_GUI_CLASS = "net.minecraft.client.gui.Gui";
    private static final String KEY_PRESS_METHOD = "keyPress";
    private static final String CHAT_INPUT_DESCRIPTOR = "(Ljava/lang/String;Z)V";
    private static final String HUD_RENDER_METHOD = "render";
    private static final String HUD_RENDER_DESCRIPTOR =
            "(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V";
    private static final String HUD_RENDER_HOOK_DESCRIPTOR = "(Ljava/lang/Object;Ljava/lang/Object;)V";
    private static final String KEY_PRESS_DESCRIPTOR = "(JILnet/minecraft/client/input/KeyEvent;)V";
    private static final String KEY_INPUT_HOOK_DESCRIPTOR = "(Ljava/lang/Object;JILjava/lang/Object;)V";
    private static final String CLIENT_TICK_METHOD = "tick";
    private static final String CLIENT_TICK_DESCRIPTOR = "()V";
    private static final String HOOK_OWNER = Type.getInternalName(MinecraftHooks.class);
    private static final String TICK_HOOK_DESCRIPTOR = "(Ljava/lang/Object;Z)V";
    private static final String MENU_HOOK_OWNER = "dev/itsdropper/vantaloader/mixin/client/VantaMenuHooks";
    private static final String MENU_HOOK_DESCRIPTOR = "(Ljava/lang/Object;)V";

    @Override public String minecraftVersion() { return VERSION; }

    @Override
    public void install(TransformationService transformations) {
        System.setProperty("vanta.minecraft.version", VERSION);
        var registry = Objects.requireNonNull(transformations, "transformations").forMod("vantaloader");
        registry.register("minecraft-1.21.11-client-tick", CLIENT_CLASS, 0, this::transformClientTick);
        registry.register("minecraft-1.21.11-keyboard-input", KEYBOARD_HANDLER_CLASS,
                0, this::transformKeyboardInput);
        registry.register("minecraft-1.21.11-client-commands", CLIENT_PACKET_LISTENER_CLASS,
                0, this::transformClientCommands);
        registry.register("minecraft-1.21.11-hud-render", HUD_GUI_CLASS,
                0, this::transformHudRender);
        registry.register("minecraft-1.21.11-title-screen", "net.minecraft.client.gui.screens.TitleScreen",
                0, (context, bytes) -> transformScreenInit(context, bytes, "title"));
        registry.register("minecraft-1.21.11-title-version-label", "net.minecraft.client.gui.GuiGraphics",
                1, this::transformGuiGraphicsVersionLabel);
        registry.register("minecraft-1.21.11-pause-screen", "net.minecraft.client.gui.screens.PauseScreen",
                0, (context, bytes) -> transformScreenInit(context, bytes, "pause"));
    }


    private byte[] transformHudRender(ClassTransformContext context, byte[] originalClass) {
        String owner = HUD_GUI_CLASS;
        String runtimeName = context.mappings().runtimeMethodName(owner, HUD_RENDER_METHOD, HUD_RENDER_DESCRIPTOR);
        String runtimeDescriptor = context.mappings().runtimeDescriptor(HUD_RENDER_DESCRIPTOR);
        ClassReader reader = new ClassReader(originalClass);
        ClassWriter writer = new HierarchyClassWriter(reader,
                ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS,
                Minecraft12111Adapter.class.getClassLoader());
        int[] matches = {0};
        ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor delegate = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!runtimeName.equals(name) || !runtimeDescriptor.equals(descriptor)) return delegate;
                matches[0]++;
                return new MethodVisitor(Opcodes.ASM9, delegate) {
                    @Override
                    public void visitInsn(int opcode) {
                        if (opcode == Opcodes.RETURN) {
                            super.visitVarInsn(Opcodes.ALOAD, 0);
                            super.visitVarInsn(Opcodes.ALOAD, 1);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOK_OWNER,
                                    "onHudRender", HUD_RENDER_HOOK_DESCRIPTOR, false);
                        }
                        super.visitInsn(opcode);
                    }
                };
            }
        };
        reader.accept(visitor, 0);
        if (matches[0] != 1) {
            throw new IllegalStateException("Expected exactly one Gui.render(GuiGraphics,DeltaTracker) method, found "
                    + matches[0] + " (" + runtimeName + runtimeDescriptor + ")");
        }
        return writer.toByteArray();
    }

    /**
     * Replaces Minecraft's own footer text at the rendering boundary.
     * This avoids assuming which screen/helper owns the footer draw call.
     */
    private byte[] transformGuiGraphicsVersionLabel(ClassTransformContext context, byte[] originalClass) {
        String owner = "net.minecraft.client.gui.GuiGraphics";
        String descriptor = "(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V";
        String runtimeName = context.mappings().runtimeMethodName(owner, "drawString", descriptor);
        String runtimeDescriptor = context.mappings().runtimeDescriptor(descriptor);
        ClassReader reader = new ClassReader(originalClass);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        int[] matches = {0};
        ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String methodDescriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor delegate = super.visitMethod(access, name, methodDescriptor, signature, exceptions);
                if (!runtimeName.equals(name) || !runtimeDescriptor.equals(methodDescriptor)) return delegate;
                matches[0]++;
                return new MethodVisitor(Opcodes.ASM9, delegate) {
                    @Override
                    public void visitCode() {
                        super.visitCode();
                        // Instance method locals: this=0, Font=1, String=2.
                        super.visitVarInsn(Opcodes.ALOAD, 2);
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, MENU_HOOK_OWNER,
                                "replaceTitleVersion", "(Ljava/lang/String;)Ljava/lang/String;", false);
                        super.visitVarInsn(Opcodes.ASTORE, 2);
                    }
                };
            }
        };
        reader.accept(visitor, 0);
        if (matches[0] != 1) {
            throw new IllegalStateException("Expected exactly one GuiGraphics.drawString(Font,String,int,int,int)"
                    + " method, found " + matches[0] + " (" + runtimeName + runtimeDescriptor + ")");
        }
        return writer.toByteArray();
    }

    private byte[] transformScreenInit(ClassTransformContext context, byte[] originalClass, String screenKind) {
        String owner = screenKind.equals("title") ? "net.minecraft.client.gui.screens.TitleScreen"
                : "net.minecraft.client.gui.screens.PauseScreen";
        String runtimeMethod = context.mappings().runtimeMethodName(owner, "init", "()V");
        String runtimeDescriptor = context.mappings().runtimeDescriptor("()V");
        ClassReader reader = new ClassReader(originalClass);
        // The receiver can be absent from a valid StackMapTable frame at a return.
        // Recompute frames with resource-only hierarchy lookup so the injected ALOAD 0
        // remains verifiable without defining Minecraft classes during transformation.
        ClassWriter writer = new HierarchyClassWriter(reader,
                ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS,
                Minecraft12111Adapter.class.getClassLoader());
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

    private byte[] transformKeyboardInput(ClassTransformContext context, byte[] originalClass) {
        String runtimeMethod = context.mappings().runtimeMethodName(
                KEYBOARD_HANDLER_CLASS, KEY_PRESS_METHOD, KEY_PRESS_DESCRIPTOR);
        String runtimeDescriptor = context.mappings().runtimeDescriptor(KEY_PRESS_DESCRIPTOR);
        ClassReader reader = new ClassReader(originalClass);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        boolean[] matched = {false};

        ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor delegate = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!runtimeMethod.equals(name) || !runtimeDescriptor.equals(descriptor)) return delegate;
                if (matched[0]) throw new IllegalStateException("Multiple matching keyboard input methods found");
                if ((access & Opcodes.ACC_STATIC) != 0) {
                    throw new IllegalStateException("Minecraft KeyboardHandler.keyPress must be an instance method");
                }
                matched[0] = true;
                return new MethodVisitor(Opcodes.ASM9, delegate) {
                    @Override
                    public void visitCode() {
                        super.visitCode();
                        super.visitVarInsn(Opcodes.ALOAD, 0);
                        super.visitVarInsn(Opcodes.LLOAD, 1);
                        super.visitVarInsn(Opcodes.ILOAD, 3);
                        super.visitVarInsn(Opcodes.ALOAD, 4);
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOK_OWNER,
                                "onKeyInput", KEY_INPUT_HOOK_DESCRIPTOR, false);
                    }
                };
            }
        };
        reader.accept(visitor, 0);
        if (!matched[0]) {
            throw new IllegalStateException("Expected Minecraft 1.21.11 KeyboardHandler.keyPress"
                    + KEY_PRESS_DESCRIPTOR + " method missing: " + runtimeMethod + runtimeDescriptor);
        }
        return writer.toByteArray();
    }


    private byte[] transformClientCommands(ClassTransformContext context, byte[] originalClass) {
        String runtimeMethod = context.mappings().runtimeMethodName(
                CHAT_SCREEN_CLASS, "handleChatInput", CHAT_INPUT_DESCRIPTOR);
        String runtimeDescriptor = context.mappings().runtimeDescriptor(CHAT_INPUT_DESCRIPTOR);
        ClassReader reader = new ClassReader(originalClass);
        ClassWriter writer = new HierarchyClassWriter(reader,
                ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS,
                Minecraft12111Adapter.class.getClassLoader());
        boolean[] matched = {false};
        ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor delegate = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!runtimeMethod.equals(name) || !runtimeDescriptor.equals(descriptor)) return delegate;
                if (matched[0]) throw new IllegalStateException("Multiple ChatScreen.handleChatInput methods found");
                if ((access & Opcodes.ACC_STATIC) != 0)
                    throw new IllegalStateException("ChatScreen.handleChatInput must be an instance method");
                matched[0] = true;
                return new MethodVisitor(Opcodes.ASM9, delegate) {
                    @Override public void visitCode() {
                        super.visitCode();
                        Label continueVanilla = new Label();
                        super.visitVarInsn(Opcodes.ALOAD, 0);
                        super.visitVarInsn(Opcodes.ALOAD, 1);
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOK_OWNER,
                                "onClientCommand", "(Ljava/lang/Object;Ljava/lang/String;)Z", false);
                        super.visitJumpInsn(Opcodes.IFEQ, continueVanilla);
                        super.visitInsn(Opcodes.RETURN);
                        super.visitLabel(continueVanilla);
                    }
                };
            }
        };
        reader.accept(visitor, 0);
        if (!matched[0]) throw new IllegalStateException(
                "Expected Minecraft 1.21.11 ChatScreen.handleChatInput(String,boolean) method missing: "
                        + runtimeMethod + runtimeDescriptor);
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

                    @Override
                    public void visitInsn(int opcode) {
                        if (opcode == Opcodes.RETURN) {
                            // No local-variable access here: Minecraft may chop local 0
                            // from a StackMapTable frame before a return instruction.
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOK_OWNER,
                                    "onClientTickEnd", "()V", false);
                        }
                        super.visitInsn(opcode);
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
