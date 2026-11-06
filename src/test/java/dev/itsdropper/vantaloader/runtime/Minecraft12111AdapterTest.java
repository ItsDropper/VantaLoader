package dev.itsdropper.vantaloader.runtime;

import dev.itsdropper.vantaloader.api.ClientTickEvent;
import dev.itsdropper.vantaloader.api.EventSubscription;
import dev.itsdropper.vantaloader.bootstrap.MinecraftHooks;
import dev.itsdropper.vantaloader.bootstrap.minecraft.Minecraft12111Adapter;
import dev.itsdropper.vantaloader.bootstrap.minecraft.MinecraftAdapterRegistry;
import dev.itsdropper.vantaloader.transformation.TransformationService;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class Minecraft12111AdapterTest {
    private static final String TARGET = "net/minecraft/client/Minecraft";

    @Test
    void registrySelectsOnlyExactVerifiedVersions() {
        assertTrue(MinecraftAdapterRegistry.find("1.21.11").isPresent());
        assertTrue(MinecraftAdapterRegistry.find("1.21.10").isEmpty());
        assertTrue(MinecraftAdapterRegistry.find("1.8.9").isEmpty());
        assertTrue(MinecraftAdapterRegistry.find("unknown").isEmpty());
        assertTrue(MinecraftAdapterRegistry.find(null).isEmpty());
    }

    @Test
    void injectsClientTickStartAndEndEvents() throws Exception {
        TransformationService transformations = new TransformationService();
        new Minecraft12111Adapter().install(transformations);
        byte[] transformed = transformations.transform(null, getClass().getClassLoader(),
                TARGET, null, null, fixture(true));
        assertNotNull(transformed);

        ModEventBus bus = new ModEventBus();
        List<ClientTickEvent> events = new ArrayList<>();
        EventSubscription subscription = bus.subscribe(ClientTickEvent.class, 0, events::add);
        MinecraftHooks.bind(bus);
        try {
            Class<?> type = new IsolatedClassLoader(getClass().getClassLoader())
                    .define(TARGET.replace('/', '.'), transformed);
            Object client = type.getConstructor().newInstance();
            type.getMethod("tick").invoke(client);
            assertEquals(List.of(ClientTickEvent.Phase.START, ClientTickEvent.Phase.END),
                    events.stream().map(ClientTickEvent::phase).toList());
            assertSame(client, events.get(0).client());
            assertSame(client, events.get(1).client());
        } finally {
            MinecraftHooks.unbind(bus);
            subscription.close();
        }
    }

    @Test
    void failsClosedWhenTargetMethodIsMissing() throws Exception {
        TransformationService transformations = new TransformationService();
        new Minecraft12111Adapter().install(transformations);
        byte[] result = transformations.transform(null, getClass().getClassLoader(),
                TARGET, null, null, fixture(false));
        assertArrayEquals(new byte[]{0, 0, 0, 0}, result);
    }

    private static byte[] fixture(boolean includeTick) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
        MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(1, 1);
        constructor.visitEnd();
        if (includeTick) {
            MethodVisitor tick = writer.visitMethod(Opcodes.ACC_PUBLIC, "tick", "()V", null, null);
            tick.visitCode();
            Label branch = new Label();
            tick.visitInsn(Opcodes.ICONST_0);
            tick.visitJumpInsn(Opcodes.IFEQ, branch);
            tick.visitInsn(Opcodes.NOP);
            tick.visitLabel(branch);
            tick.visitInsn(Opcodes.RETURN);
            tick.visitMaxs(0, 0);
            tick.visitEnd();
        }
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static final class IsolatedClassLoader extends ClassLoader {
        private IsolatedClassLoader(ClassLoader parent) { super(parent); }
        private Class<?> define(String name, byte[] bytes) { return defineClass(name, bytes, 0, bytes.length); }
    }
}
