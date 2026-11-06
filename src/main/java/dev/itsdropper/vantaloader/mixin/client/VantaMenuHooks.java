package dev.itsdropper.vantaloader.mixin.client;

import dev.itsdropper.vantaloader.api.ModMenuEntry;
import dev.itsdropper.vantaloader.runtime.ModRuntime;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Reflection-isolated Minecraft screen bridge; game classes never enter the public loader API. */
public final class VantaMenuHooks {
    private static final Logger LOGGER = Logger.getLogger("VantaLoader.ModMenu");
    private static volatile ModRuntime runtime;
    private static volatile Object parentScreen;
    private static volatile Class<?> generatedScreenClass;

    private VantaMenuHooks() { }

    public static void bind(ModRuntime modRuntime) {
        runtime = Objects.requireNonNull(modRuntime, "modRuntime");
    }

    public static void addMenuButton(Object hostScreen) {
        try {
            int width = numberField(hostScreen, "width");
            int height = numberField(hostScreen, "height");
            addButton(hostScreen, "VantaLoader", width - 112, height - 28, 104, 20,
                    () -> openMenu(hostScreen));
        } catch (Throwable failure) {
            LOGGER.log(Level.WARNING, "Could not add VantaLoader button to " + hostScreen.getClass().getName(), failure);
        }
    }

    private static void openMenu(Object parent) {
        try {
            parentScreen = parent;
            Class<?> screenType = generatedScreenClass();
            Class<?> componentType = Class.forName("net.minecraft.network.chat.Component");
            Object title = componentType.getMethod("literal", String.class).invoke(null, "VantaLoader");
            Object screen = screenType.getConstructor(componentType).newInstance(title);
            setMinecraftScreen(screen);
        } catch (Throwable failure) {
            LOGGER.log(Level.SEVERE, "Could not open the VantaLoader mod menu", failure);
        }
    }

    private static Class<?> generatedScreenClass() throws Exception {
        Class<?> cached = generatedScreenClass;
        if (cached != null) return cached;
        synchronized (VantaMenuHooks.class) {
            if (generatedScreenClass != null) return generatedScreenClass;
            Class<?> base = Class.forName("net.minecraft.client.gui.screens.Screen");
            String internalName = "net/minecraft/client/gui/screens/VantaLoaderGeneratedScreen";
            String hooks = Type.getInternalName(VantaMenuHooks.class);
            String component = "net/minecraft/network/chat/Component";
            String graphics = "net/minecraft/client/gui/GuiGraphics";
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER,
                    internalName, null, Type.getInternalName(base), null);

            MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>",
                    "(L" + component + ";)V", null, null);
            constructor.visitCode();
            constructor.visitVarInsn(Opcodes.ALOAD, 0);
            constructor.visitVarInsn(Opcodes.ALOAD, 1);
            constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, Type.getInternalName(base),
                    "<init>", "(L" + component + ";)V", false);
            constructor.visitInsn(Opcodes.RETURN);
            constructor.visitMaxs(0, 0);
            constructor.visitEnd();

            MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "init", "()V", null, null);
            init.visitCode();
            init.visitVarInsn(Opcodes.ALOAD, 0);
            init.visitMethodInsn(Opcodes.INVOKESPECIAL, Type.getInternalName(base), "init", "()V", false);
            init.visitVarInsn(Opcodes.ALOAD, 0);
            init.visitMethodInsn(Opcodes.INVOKESTATIC, hooks, "initialize", "(Ljava/lang/Object;)V", false);
            init.visitInsn(Opcodes.RETURN);
            init.visitMaxs(0, 0);
            init.visitEnd();

            MethodVisitor render = writer.visitMethod(Opcodes.ACC_PUBLIC, "render",
                    "(L" + graphics + ";IIF)V", null, null);
            render.visitCode();
            render.visitVarInsn(Opcodes.ALOAD, 0);
            render.visitVarInsn(Opcodes.ALOAD, 1);
            render.visitVarInsn(Opcodes.ILOAD, 2);
            render.visitVarInsn(Opcodes.ILOAD, 3);
            render.visitVarInsn(Opcodes.FLOAD, 4);
            render.visitMethodInsn(Opcodes.INVOKESPECIAL, Type.getInternalName(base), "render",
                    "(L" + graphics + ";IIF)V", false);
            render.visitVarInsn(Opcodes.ALOAD, 0);
            render.visitVarInsn(Opcodes.ALOAD, 1);
            render.visitVarInsn(Opcodes.ILOAD, 2);
            render.visitVarInsn(Opcodes.ILOAD, 3);
            render.visitVarInsn(Opcodes.FLOAD, 4);
            render.visitMethodInsn(Opcodes.INVOKESTATIC, hooks, "render",
                    "(Ljava/lang/Object;Ljava/lang/Object;IIF)V", false);
            render.visitInsn(Opcodes.RETURN);
            render.visitMaxs(0, 0);
            render.visitEnd();

            MethodVisitor close = writer.visitMethod(Opcodes.ACC_PUBLIC, "onClose", "()V", null, null);
            close.visitCode();
            close.visitVarInsn(Opcodes.ALOAD, 0);
            close.visitMethodInsn(Opcodes.INVOKESTATIC, hooks, "close", "(Ljava/lang/Object;)V", false);
            close.visitInsn(Opcodes.RETURN);
            close.visitMaxs(0, 0);
            close.visitEnd();
            writer.visitEnd();

            generatedScreenClass = MethodHandles.privateLookupIn(base, MethodHandles.lookup())
                    .defineClass(writer.toByteArray());
            return generatedScreenClass;
        }
    }

    public static void initialize(Object screen) {
        ModRuntime current = runtime;
        if (current == null) return;
        try {
            int width = numberField(screen, "width");
            int height = numberField(screen, "height");
            List<ModMenuEntry> entries = current.modMenuEntries();
            for (int i = 0; i < entries.size(); i++) {
                ModMenuEntry entry = entries.get(i);
                if (!entry.hasConfigScreen()) continue;
                int y = 74 + i * 38;
                addButton(screen, "Settings", width - 132, y, 112, 20,
                        () -> openModSettings(screen, entry.id()));
            }
            addButton(screen, "Back", width - 104, height - 30, 88, 20,
                    () -> setMinecraftScreen(parentScreen));
        } catch (Throwable failure) {
            LOGGER.log(Level.WARNING, "Could not initialize VantaLoader screen controls", failure);
        }
    }

    private static void openModSettings(Object menu, String modId) {
        ModRuntime current = runtime;
        if (current == null) return;
        try {
            Object configScreen = current.createModConfigScreen(modId, menu);
            if (configScreen == null) throw new IllegalStateException("Mod returned a null settings screen");
            setMinecraftScreen(configScreen);
        } catch (Throwable failure) {
            LOGGER.log(Level.WARNING, "Could not open settings for mod " + modId, failure);
        }
    }

    public static void render(Object screen, Object graphics, int mouseX, int mouseY, float partialTick) {
        ModRuntime current = runtime;
        if (current == null) return;
        try {
            int width = numberField(screen, "width");
            int height = numberField(screen, "height");
            Object font = objectField(screen, "font");
            List<ModMenuEntry> entries = current.modMenuEntries();
            fill(graphics, 12, 12, width - 12, height - 12, 0xD9101722);
            fill(graphics, 12, 12, width - 12, 14, 0xFF55A8FF);
            centered(graphics, font, "VantaLoader " + System.getProperty("vanta.minecraft.version", "unknown"),
                    width / 2, 26, 0xFFFFFFFF);
            centered(graphics, font, "Loaded " + entries.size()
                    + (entries.size() == 1 ? " mod" : " mods"), width / 2, 43, 0xFFB7C7D9);

            int visible = Math.max(0, Math.min(entries.size(), (height - 112) / 38));
            for (int i = 0; i < visible; i++) {
                ModMenuEntry entry = entries.get(i);
                int y = 62 + i * 38;
                fill(graphics, 22, y, width - 22, y + 34, 0xA51E2A39);
                fill(graphics, 22, y, 25, y + 34, 0xFF4B9DEB);
                draw(graphics, font, entry.name() + "  " + entry.version(), 34, y + 3, 0xFFFFFFFF);
                String details = entry.description() == null ? "No description provided" : entry.description();
                if (entry.license() != null) details += "  •  " + entry.license();
                if (entry.website() != null) details += "  •  " + entry.website();
                if (entry.sources() != null) details += "  •  Source: " + entry.sources();
                draw(graphics, font, truncate(details, Math.max(12, (width - 190) / 6)),
                        34, y + 18, 0xFFB7C7D9);
            }
            if (entries.size() > visible) {
                centered(graphics, font, (entries.size() - visible) + " more mods — scroll support coming later",
                        width / 2, height - 48, 0xFF8EA3BA);
            }
        } catch (Throwable failure) {
            LOGGER.log(Level.FINE, "VantaLoader screen rendering skipped", failure);
        }
    }

    public static void close(Object screen) {
        setMinecraftScreen(parentScreen);
    }

    private static void addButton(Object screen, String label, int x, int y, int width, int height, Runnable action)
            throws Exception {
        ClassLoader loader = screen.getClass().getClassLoader();
        Class<?> componentType = Class.forName("net.minecraft.network.chat.Component", true, loader);
        Class<?> buttonType = Class.forName("net.minecraft.client.gui.components.Button", true, loader);
        Class<?> pressType = Class.forName("net.minecraft.client.gui.components.Button$OnPress", true, loader);
        Object component = componentType.getMethod("literal", String.class).invoke(null, label);
        Object press = Proxy.newProxyInstance(loader, new Class<?>[]{pressType}, (proxy, method, args) -> {
            if (method.getName().equals("onPress")) action.run();
            return null;
        });
        Method builderMethod = null;
        for (Method method : buttonType.getMethods()) {
            if (method.getName().equals("builder") && method.getParameterCount() == 2
                    && method.getParameterTypes()[0].isAssignableFrom(componentType)
                    && method.getParameterTypes()[1].isAssignableFrom(pressType)) {
                builderMethod = method;
                break;
            }
        }
        if (builderMethod == null) throw new NoSuchMethodException("Button.builder(Component, OnPress)");
        Object builder = builderMethod.invoke(null, component, press);
        Method bounds = builder.getClass().getMethod("bounds", int.class, int.class, int.class, int.class);
        bounds.invoke(builder, x, y, width, height);
        Object button = builder.getClass().getMethod("build").invoke(builder);
        Method add = findMethod(screen.getClass(), "addRenderableWidget", 1);
        add.setAccessible(true);
        add.invoke(screen, button);
    }

    private static void setMinecraftScreen(Object target) {
        if (target == null) return;
        try {
            Class<?> minecraft = Class.forName("net.minecraft.client.Minecraft");
            Object instance = minecraft.getMethod("getInstance").invoke(null);
            for (Method method : minecraft.getMethods()) {
                if (method.getName().equals("setScreen") && method.getParameterCount() == 1
                        && method.getParameterTypes()[0].isInstance(target)) {
                    method.invoke(instance, target);
                    return;
                }
            }
            throw new NoSuchMethodException("Minecraft.setScreen(Screen)");
        } catch (Throwable failure) {
            LOGGER.log(Level.WARNING, "Could not switch Minecraft screens", failure);
        }
    }

    private static void fill(Object graphics, int x1, int y1, int x2, int y2, int color) throws Exception {
        graphics.getClass().getMethod("fill", int.class, int.class, int.class, int.class, int.class)
                .invoke(graphics, x1, y1, x2, y2, color);
    }

    private static void centered(Object graphics, Object font, String text, int x, int y, int color) throws Exception {
        Class<?> component = Class.forName("net.minecraft.network.chat.Component");
        Object value = component.getMethod("literal", String.class).invoke(null, text);
        for (Method method : graphics.getClass().getMethods()) {
            if (method.getName().equals("drawCenteredString") && method.getParameterCount() == 5) {
                method.invoke(graphics, font, value, x, y, color);
                return;
            }
        }
    }

    private static void draw(Object graphics, Object font, String text, int x, int y, int color) throws Exception {
        Class<?> component = Class.forName("net.minecraft.network.chat.Component");
        Object value = component.getMethod("literal", String.class).invoke(null, text);
        for (Method method : graphics.getClass().getMethods()) {
            if (method.getName().equals("drawString") && method.getParameterCount() == 5
                    && method.getParameterTypes()[1].isInstance(value)) {
                method.invoke(graphics, font, value, x, y, color);
                return;
            }
        }
    }

    private static int numberField(Object target, String name) throws Exception {
        return ((Number) field(target.getClass(), name).get(target)).intValue();
    }

    private static Object objectField(Object target, String name) throws Exception {
        return field(target.getClass(), name).get(target);
    }

    private static Field field(Class<?> type, String name) throws Exception {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }

    private static Method findMethod(Class<?> type, String name, int parameterCount) throws NoSuchMethodException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == parameterCount) return method;
            }
        }
        throw new NoSuchMethodException(type.getName() + "." + name);
    }

    private static String truncate(String value, int max) {
        if (value.length() <= max) return value;
        return value.substring(0, Math.max(0, max - 1)) + "…";
    }
}
