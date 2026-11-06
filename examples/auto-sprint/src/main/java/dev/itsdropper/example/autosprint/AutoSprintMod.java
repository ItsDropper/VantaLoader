package dev.itsdropper.example.autosprint;

import dev.itsdropper.vantaloader.api.ClassTransformContext;
import dev.itsdropper.vantaloader.api.HierarchyClassWriter;
import dev.itsdropper.vantaloader.api.MinecraftCapability;
import dev.itsdropper.vantaloader.api.MinecraftMappingView;
import dev.itsdropper.vantaloader.api.ModContext;
import dev.itsdropper.vantaloader.api.VantaMod;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Automatically sprints while forward is held, using verified runtime mappings. */
public final class AutoSprintMod implements VantaMod {
    private static final String TARGET = "net.minecraft.client.player.LocalPlayer";
    private static final String MINECRAFT = "net.minecraft.client.Minecraft";
    private static final String OPTIONS = "net.minecraft.client.Options";
    private static final String KEY_MAPPING = "net.minecraft.client.KeyMapping";
    private static final String ENTITY = "net.minecraft.world.entity.Entity";
    private static final String LIVING_ENTITY = "net.minecraft.world.entity.LivingEntity";

    private ModContext context;
    private boolean enabled = true;

    public AutoSprintMod() { }

    @Override
    public void onInitialize(ModContext context) throws Exception {
        this.context = context;
        if (!"1.21.11".equals(context.minecraftVersion())
                || !context.minecraftCapabilities().supportsAll(
                        MinecraftCapability.CLASS_TRANSFORMATIONS,
                        MinecraftCapability.OFFICIAL_MOJANG_MAPPINGS)) {
            context.logger().warning("Auto Sprint requires Minecraft 1.21.11, class transformations, and official Mojang mappings; disabled");
            enabled = false;
            return;
        }

        Path config = context.configDirectory().resolve("enabled.properties");
        Files.createDirectories(config.getParent());
        Properties properties = new Properties();
        if (Files.isRegularFile(config)) {
            try (var input = Files.newInputStream(config)) {
                properties.load(input);
            }
            enabled = Boolean.parseBoolean(properties.getProperty("enabled", "true"));
        } else {
            properties.setProperty("enabled", "true");
            try (var output = Files.newOutputStream(config)) {
                properties.store(output, "Vanta Auto Sprint settings");
            }
        }
        if (!enabled) {
            context.logger().info("Auto Sprint is disabled in enabled.properties");
            return;
        }

        context.transformations().register("auto-sprint-local-player", TARGET, 100,
                this::transformLocalPlayer);
        context.logger().info("Auto Sprint transformer registered");
    }

    private byte[] transformLocalPlayer(ClassTransformContext context, byte[] originalClass)
            throws IOException {
        MinecraftMappingView mappings = context.mappings();
        String runtimeTarget = internal(mappings.runtimeClassName(TARGET));
        String tickName = mappings.runtimeMethodName(TARGET, "aiStep", "()V");
        String tickDescriptor = mappings.runtimeDescriptor("()V");

        String minecraft = internal(mappings.runtimeClassName(MINECRAFT));
        String options = internal(mappings.runtimeClassName(OPTIONS));
        String keyMapping = internal(mappings.runtimeClassName(KEY_MAPPING));
        String entity = internal(mappings.runtimeClassName(ENTITY));
        String livingEntity = internal(mappings.runtimeClassName(LIVING_ENTITY));

        String getInstance = mappings.runtimeMethodName(MINECRAFT, "getInstance",
                "()Lnet/minecraft/client/Minecraft;");
        String optionsField = mappings.runtimeFieldName(MINECRAFT, "options",
                "Lnet/minecraft/client/Options;");
        String keyUpField = mappings.runtimeFieldName(OPTIONS, "keyUp",
                "Lnet/minecraft/client/KeyMapping;");
        String keyShiftField = mappings.runtimeFieldName(OPTIONS, "keyShift",
                "Lnet/minecraft/client/KeyMapping;");
        String isDown = mappings.runtimeMethodName(KEY_MAPPING, "isDown", "()Z");
        String isUsingItem = mappings.runtimeMethodName(LIVING_ENTITY, "isUsingItem", "()Z");
        String isSprinting = mappings.runtimeMethodName(ENTITY, "isSprinting", "()Z");
        String setSprinting = mappings.runtimeMethodName(ENTITY, "setSprinting", "(Z)V");

        ClassReader reader = new ClassReader(originalClass);
        ClassWriter writer = new HierarchyClassWriter(reader,
                ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS,
                Thread.currentThread().getContextClassLoader());
        int[] matches = {0};
        ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor delegate = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!tickName.equals(name) || !tickDescriptor.equals(descriptor)) return delegate;
                matches[0]++;
                return new MethodVisitor(Opcodes.ASM9, delegate) {
                    @Override
                    public void visitCode() {
                        super.visitCode();
                        Label skip = new Label();

                        super.visitVarInsn(Opcodes.ALOAD, 0);
                        super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, livingEntity,
                                isUsingItem, "()Z", false);
                        super.visitJumpInsn(Opcodes.IFNE, skip);

                        super.visitMethodInsn(Opcodes.INVOKESTATIC, minecraft,
                                getInstance, "()L" + minecraft + ";", false);
                        super.visitFieldInsn(Opcodes.GETFIELD, minecraft, optionsField, "L" + options + ";");
                        super.visitFieldInsn(Opcodes.GETFIELD, options, keyShiftField, "L" + keyMapping + ";");
                        super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, keyMapping, isDown, "()Z", false);
                        super.visitJumpInsn(Opcodes.IFNE, skip);

                        super.visitMethodInsn(Opcodes.INVOKESTATIC, minecraft,
                                getInstance, "()L" + minecraft + ";", false);
                        super.visitFieldInsn(Opcodes.GETFIELD, minecraft, optionsField, "L" + options + ";");
                        super.visitFieldInsn(Opcodes.GETFIELD, options, keyUpField, "L" + keyMapping + ";");
                        super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, keyMapping, isDown, "()Z", false);
                        super.visitJumpInsn(Opcodes.IFEQ, skip);

                        super.visitVarInsn(Opcodes.ALOAD, 0);
                        super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, entity, isSprinting, "()Z", false);
                        super.visitJumpInsn(Opcodes.IFNE, skip);

                        super.visitVarInsn(Opcodes.ALOAD, 0);
                        super.visitInsn(Opcodes.ICONST_1);
                        super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, entity, setSprinting, "(Z)V", false);
                        super.visitLabel(skip);
                    }
                };
            }
        };
        reader.accept(visitor, 0);
        if (matches[0] != 1) {
            throw new IllegalStateException("Expected exactly one mapped LocalPlayer.aiStep()V method, found "
                    + matches[0] + " in " + runtimeTarget);
        }
        context.logger().fine("Applied Auto Sprint to " + runtimeTarget + "." + tickName);
        return writer.toByteArray();
    }

    private static String internal(String binaryName) {
        return binaryName.replace('.', '/');
    }

    @Override
    public void onShutdown() {
        if (context != null) context.logger().info("Auto Sprint stopped");
    }
}
