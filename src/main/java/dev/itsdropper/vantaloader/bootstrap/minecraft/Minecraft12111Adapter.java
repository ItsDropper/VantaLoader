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
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.commons.Method;
import java.util.Objects;

/** Initial exact-version client adapter for Minecraft 1.21.11. */
public final class Minecraft12111Adapter implements MinecraftAdapter {
    public static final String VERSION = "1.21.11";
    public static final String CLIENT_CLASS = "net.minecraft.client.Minecraft";
    private static final String CLIENT_TICK_METHOD = "tick";
    private static final String CLIENT_TICK_DESCRIPTOR = "()V";
    private static final String HOOK_OWNER = Type.getInternalName(MinecraftHooks.class);
    private static final Method HOOK_METHOD = new Method("onClientTick", "(Ljava/lang/Object;Z)V");

    @Override public String minecraftVersion() { return VERSION; }

    @Override
    public void install(TransformationService transformations) {
        Objects.requireNonNull(transformations, "transformations")
                .forMod("vantaloader")
                .register("minecraft-1.21.11-client-tick", CLIENT_CLASS, 0, this::transformClientTick);
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
                return new AdviceAdapter(Opcodes.ASM9, delegate, access, name, descriptor) {
                    @Override protected void onMethodEnter() {
                        loadThis();
                        push(true);
                        invokeStatic(Type.getObjectType(HOOK_OWNER), HOOK_METHOD);
                    }
                    @Override protected void onMethodExit(int opcode) {
                        if (opcode != RETURN) return;
                        loadThis();
                        push(false);
                        invokeStatic(Type.getObjectType(HOOK_OWNER), HOOK_METHOD);
                    }
                };
            }
        };
        reader.accept(visitor, 0);
        if (!matched[0]) {
            throw new IllegalStateException("Expected Minecraft 1.21.11 target missing: "
                    + CLIENT_CLASS + "." + runtimeMethod + runtimeDescriptor);
        }
        return writer.toByteArray();
    }
}
