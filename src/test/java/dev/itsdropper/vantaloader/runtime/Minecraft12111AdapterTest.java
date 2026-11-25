package dev.itsdropper.vantaloader.runtime;

import dev.itsdropper.vantaloader.api.ClientTickEvent;
import dev.itsdropper.vantaloader.api.KeyInputEvent;
import dev.itsdropper.vantaloader.api.EventSubscription;
import dev.itsdropper.vantaloader.bootstrap.MinecraftHooks;
import dev.itsdropper.vantaloader.bootstrap.minecraft.Minecraft12111Adapter;
import dev.itsdropper.vantaloader.bootstrap.minecraft.MinecraftAdapterRegistry;
import dev.itsdropper.vantaloader.transformation.TransformationService;
import dev.itsdropper.vantaloader.mapping.MinecraftMappings;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class Minecraft12111AdapterTest {
    @TempDir Path temporaryDirectory;
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
    void injectsMappedKeyboardInputAndPublishesTypedVmaEvent() throws Exception {
        Path mappingsFile = temporaryDirectory.resolve("client.txt");
        Files.writeString(mappingsFile, """
                net.minecraft.client.KeyboardHandler -> b:
                    1:1:void keyPress(long,int,net.minecraft.client.input.KeyEvent) -> a
                net.minecraft.client.input.KeyEvent -> c:
                    1:1:int key() -> a
                    1:1:int scancode() -> b
                    1:1:int modifiers() -> c
                """);
        MinecraftMappings mappings = MinecraftMappings.read(mappingsFile);
        MinecraftHooks.configureMappings(mappings);
        TransformationService transformations = new TransformationService(mappings);
        new Minecraft12111Adapter().install(transformations);

        byte[] transformed = transformations.transform(getClass().getClassLoader(),
                "b", null, null, keyboardHandlerFixture());
        assertNotNull(transformed);

        ModEventBus bus = new ModEventBus();
        AtomicReference<KeyInputEvent> received = new AtomicReference<>();
        EventSubscription subscription = bus.subscribe(KeyInputEvent.class, 0, received::set);
        MinecraftHooks.bind(bus);
        try {
            IsolatedClassLoader loader = new IsolatedClassLoader(getClass().getClassLoader());
            Class<?> keyEventType = loader.define("c", keyEventFixture());
            Class<?> handlerType = loader.define("b", transformed);
            Object handler = handlerType.getConstructor().newInstance();
            Object keyEvent = keyEventType.getConstructor(int.class, int.class, int.class)
                    .newInstance(65, 30, 3);

            handlerType.getMethod("a", long.class, int.class, keyEventType)
                    .invoke(handler, 0x1234L, KeyInputEvent.PRESS, keyEvent);

            KeyInputEvent event = received.get();
            assertNotNull(event);
            assertSame(handler, event.keyboardHandler());
            assertEquals(0x1234L, event.windowHandle());
            assertEquals(65, event.keyCode());
            assertEquals(30, event.scanCode());
            assertEquals(KeyInputEvent.PRESS, event.action());
            assertEquals(3, event.modifiers());
            assertTrue(event.pressed());
            assertFalse(event.released());
            assertFalse(event.repeated());
        } finally {
            MinecraftHooks.unbind(bus);
            subscription.close();
        }
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
    void injectsTitleAndPauseScreenMenuHooks() throws Exception {
        TransformationService transformations = new TransformationService();
        new Minecraft12111Adapter().install(transformations);
        assertScreenHook(transformations, "net/minecraft/client/gui/screens/TitleScreen");
        assertTitleVersionHook(transformations);
        assertScreenHook(transformations, "net/minecraft/client/gui/screens/PauseScreen");
    }

    private static void assertTitleVersionHook(TransformationService transformations) throws Exception {
        byte[] transformed = transformations.transform(null, Minecraft12111AdapterTest.class.getClassLoader(),
                "net/minecraft/client/gui/GuiGraphics", null, null, graphicsFixture());
        assertNotNull(transformed);
        org.objectweb.asm.ClassReader reader = new org.objectweb.asm.ClassReader(transformed);
        boolean[] found = {false};
        reader.accept(new org.objectweb.asm.ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String method,
                                                String methodDescriptor, boolean isInterface) {
                        if (opcode == Opcodes.INVOKESTATIC
                                && owner.equals("dev/itsdropper/vantaloader/mixin/client/VantaMenuHooks")
                                && method.equals("replaceTitleVersion")
                                && methodDescriptor.equals("(Ljava/lang/String;)Ljava/lang/String;")) {
                            found[0] = true;
                        }
                    }
                };
            }
        }, 0);
        assertTrue(found[0], "Expected GuiGraphics native version draw method to call VantaLoader replacement");
    }

    private static void assertScreenHook(TransformationService transformations, String target) throws Exception {
        byte[] original = screenFixture(target);
        new IsolatedClassLoader(Minecraft12111AdapterTest.class.getClassLoader())
                .define(target.replace('/', '.'), original);
        byte[] transformed = transformations.transform(null, Minecraft12111AdapterTest.class.getClassLoader(),
                target, null, null, original);
        assertNotNull(transformed);
        IsolatedClassLoader verificationLoader = new IsolatedClassLoader(
                Minecraft12111AdapterTest.class.getClassLoader());
        verificationLoader.define(target.replace('/', '.'), transformed);
        org.objectweb.asm.ClassReader reader = new org.objectweb.asm.ClassReader(transformed);
        boolean[] found = {false};
        reader.accept(new org.objectweb.asm.ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String method,
                                                String methodDescriptor, boolean isInterface) {
                        if (opcode == Opcodes.INVOKESTATIC
                                && owner.equals("dev/itsdropper/vantaloader/mixin/client/VantaMenuHooks")
                                && method.equals("addMenuButton")
                                && methodDescriptor.equals("(Ljava/lang/Object;)V")) {
                            found[0] = true;
                        }
                    }
                };
            }
        }, 0);
        assertTrue(found[0], "Expected Vanta menu hook in " + target);
    }

    private static byte[] graphicsFixture() {
        String target = "net/minecraft/client/gui/GuiGraphics";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, target, null, "java/lang/Object", null);
        MethodVisitor draw = writer.visitMethod(Opcodes.ACC_PUBLIC, "drawString",
                "(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V", null, null);
        draw.visitCode();
        draw.visitInsn(Opcodes.RETURN);
        draw.visitMaxs(0, 0);
        draw.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] screenFixture(String internalName) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null);
        MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(1, 1);
        constructor.visitEnd();
        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "init", "()V", null, null);
        init.visitCode();
        Label branch = new Label();
        init.visitInsn(Opcodes.ICONST_0);
        init.visitJumpInsn(Opcodes.IFEQ, branch);
        init.visitInsn(Opcodes.RETURN);
        init.visitLabel(branch);
        init.visitFrame(Opcodes.F_CHOP, 1, null, 0, null);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(1, 1);
        init.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    @Test
    void failsClosedWhenTargetMethodIsMissing() throws Exception {
        TransformationService transformations = new TransformationService();
        new Minecraft12111Adapter().install(transformations);
        byte[] result = transformations.transform(null, getClass().getClassLoader(),
                TARGET, null, null, fixture(false));
        assertArrayEquals(new byte[]{0, 0, 0, 0}, result);
    }

    private static byte[] keyboardHandlerFixture() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "b", null, "java/lang/Object", null);
        MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(1, 1);
        constructor.visitEnd();

        MethodVisitor keyPress = writer.visitMethod(Opcodes.ACC_PUBLIC, "a", "(JILc;)V", null, null);
        keyPress.visitCode();
        keyPress.visitInsn(Opcodes.RETURN);
        keyPress.visitMaxs(0, 5);
        keyPress.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] keyEventFixture() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "c", null, "java/lang/Object", null);
        writer.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "key", "I", null, null).visitEnd();
        writer.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "scanCode", "I", null, null).visitEnd();
        writer.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "modifiers", "I", null, null).visitEnd();
        MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(III)V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitVarInsn(Opcodes.ILOAD, 1);
        constructor.visitFieldInsn(Opcodes.PUTFIELD, "c", "key", "I");
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitVarInsn(Opcodes.ILOAD, 2);
        constructor.visitFieldInsn(Opcodes.PUTFIELD, "c", "scanCode", "I");
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitVarInsn(Opcodes.ILOAD, 3);
        constructor.visitFieldInsn(Opcodes.PUTFIELD, "c", "modifiers", "I");
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(3, 4);
        constructor.visitEnd();
        addIntAccessor(writer, "a", "key");
        addIntAccessor(writer, "b", "scanCode");
        addIntAccessor(writer, "c", "modifiers");
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void addIntAccessor(ClassWriter writer, String methodName, String fieldName) {
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, methodName, "()I", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitFieldInsn(Opcodes.GETFIELD, "c", fieldName, "I");
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(1, 1);
        method.visitEnd();
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
