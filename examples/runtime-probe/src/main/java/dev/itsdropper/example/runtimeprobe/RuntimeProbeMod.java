package dev.itsdropper.example.runtimeprobe;

import dev.itsdropper.vantaloader.api.ClassTransformContext;
import dev.itsdropper.vantaloader.api.ClientTickEvent;
import dev.itsdropper.vantaloader.api.ModContext;
import dev.itsdropper.vantaloader.api.VantaMod;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * CI integration probe: observes actual Minecraft tick callbacks and composes
 * a second mod-owned transformation onto the loader's Minecraft client patch.
 */
public final class RuntimeProbeMod implements VantaMod {
    private static final String CLIENT = "net.minecraft.client.Minecraft";
    private static final String MARKER_METHOD = "vantaLoaderRuntimeProbeMarker";
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
        context.transformations().register("runtime-probe-client-compose", CLIENT, 100,
                this::addCompositionMarker);
        context.logger().info("Runtime integration probe registered");
    }

    private byte[] addCompositionMarker(ClassTransformContext context, byte[] original) {
        String runtimeName = context.mappings().runtimeClassName(CLIENT).replace('.', '/');
        ClassReader reader = new ClassReader(original);
        if (!runtimeName.equals(reader.getClassName())) {
            throw new IllegalStateException("Runtime probe received unexpected class " + reader.getClassName());
        }
        boolean[] exists = {false};
        ClassWriter writer = new ClassWriter(reader, 0);
        ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                if (MARKER_METHOD.equals(name) && "()V".equals(descriptor)) exists[0] = true;
                return super.visitMethod(access, name, descriptor, signature, exceptions);
            }
        };
        reader.accept(visitor, 0);
        if (exists[0]) throw new IllegalStateException("Runtime probe marker method already exists");
        MethodVisitor marker = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                MARKER_METHOD, "()V", null, null);
        marker.visitCode();
        marker.visitInsn(Opcodes.RETURN);
        marker.visitMaxs(0, 0);
        marker.visitEnd();
        contextTargetVerified(context);
        return writer.toByteArray();
    }

    private static void contextTargetVerified(ClassTransformContext context) {
        if (!CLIENT.equals(context.targetClassName())) {
            throw new IllegalStateException("Runtime probe transformation target metadata mismatch");
        }
    }

    @Override
    public void onShutdown() { }
}
