package dev.itsdropper.example.runtimeprobe;

import dev.itsdropper.vantaloader.api.ClassTransformContext;
import dev.itsdropper.vantaloader.api.ClientTickEvent;
import dev.itsdropper.vantaloader.api.ModContext;
import dev.itsdropper.vantaloader.api.VantaMod;
import dev.itsdropper.vantaloader.bootstrap.MinecraftHooks;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * CI integration probe: observes actual Minecraft client tick callbacks and
 * composes a second mod-owned LocalPlayer transformation after Auto Sprint.
 */
public final class RuntimeProbeMod implements VantaMod {
    private static final String LOCAL_PLAYER = "net.minecraft.client.player.LocalPlayer";
    private static final String HOOK_OWNER = org.objectweb.asm.Type.getInternalName(MinecraftHooks.class);
    private final AtomicBoolean startObserved = new AtomicBoolean();
    private final AtomicBoolean endObserved = new AtomicBoolean();

    public RuntimeProbeMod() { }

    @Override
    public void onInitialize(ModContext context) {
        context.events().subscribe(ClientTickEvent.class, 0, event -> {
            if (event.phase() == ClientTickEvent.Phase.START && startObserved.compareAndSet(false, true)) {
                context.logger().info("VantaLoader integration probe: client tick START observed");
            } else if (event.phase() == ClientTickEvent.Phase.END && endObserved.compareAndSet(false, true)) {
                context.logger().info("VantaLoader integration probe: client tick END observed");
            }
        });
        context.transformations().register("runtime-probe-local-player-tick", LOCAL_PLAYER, 200,
                this::instrumentLocalPlayerTick);
        context.logger().info("Runtime integration probe registered");
    }

    private byte[] instrumentLocalPlayerTick(ClassTransformContext context, byte[] original) {
        String runtimeName = context.mappings().runtimeClassName(LOCAL_PLAYER).replace('.', '/');
        String tickName = context.mappings().runtimeMethodName(LOCAL_PLAYER, "aiStep", "()V");
        String tickDescriptor = context.mappings().runtimeDescriptor("()V");
        ClassReader reader = new ClassReader(original);
        if (!runtimeName.equals(reader.getClassName())) {
            throw new IllegalStateException("Runtime probe received unexpected class " + reader.getClassName());
        }
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
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
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOK_OWNER,
                                "onLocalPlayerTick", "()V", false);
                    }
                };
            }
        };
        reader.accept(visitor, 0);
        if (matches[0] != 1) {
            throw new IllegalStateException("Expected exactly one mapped LocalPlayer.aiStep()V method, found "
                    + matches[0] + " in " + runtimeName);
        }
        return writer.toByteArray();
    }

    @Override
    public void onShutdown() { }
}
