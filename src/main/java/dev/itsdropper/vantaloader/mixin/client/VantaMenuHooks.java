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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Reflection-isolated Minecraft screen bridge; game classes never enter the public loader API. */
public final class VantaMenuHooks {
    private static final Logger LOGGER = Logger.getLogger("VantaLoader.ModMenu");
    private static final int ROW_HEIGHT = 38;
    private static final int LIST_TOP = 62;
    private static final int LIST_BOTTOM_MARGIN = 54;
    private static volatile ModRuntime runtime;
    private static volatile Object parentScreen;
    private static volatile Class<?> generatedScreenClass;
    private static volatile int scrollOffset;
    private static final Map<Object, Map<String, Object>> CONFIG_BUTTONS =
            java.util.Collections.synchronizedMap(new WeakHashMap<>());

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

            MethodVisitor scroll = writer.visitMethod(Opcodes.ACC_PUBLIC, "mouseScrolled", "(DDDD)Z", null, null);
            scroll.visitCode();
            scroll.visitVarInsn(Opcodes.ALOAD, 0);
            scroll.visitVarInsn(Opcodes.DLOAD, 1);
            scroll.visitVarInsn(Opcodes.DLOAD, 3);
            scroll.visitVarInsn(Opcodes.DLOAD, 5);
            scroll.visitVarInsn(Opcodes.DLOAD, 7);
            scroll.visitMethodInsn(Opcodes.INVOKESTATIC, hooks, "scroll",
                    "(Ljava/lang/Object;DDDD)Z", false);
            scroll.visitInsn(Opcodes.IRETURN);
            scroll.visitMaxs(0, 0);
            scroll.visitEnd();

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
            scrollOffset = 0;
            List<ModMenuEntry> entries = current.modMenuEntries();
            Map<String, Object> buttons = new LinkedHashMap<>();
            for (ModMenuEntry entry : entries) {
                if (!entry.hasConfigScreen()) continue;
                Object button = addButton(screen, "Settings", width - 132, -1000, 112, 20,
                        () -> openModSettings(screen, entry.id()));
                buttons.put(entry.id(), button);
            }
            CONFIG_BUTTONS.put(screen, buttons);
            updateConfigButtonPositions(screen, entries, visibleRows(height));
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

    public static boolean scroll(Object screen, double mouseX, double mouseY,
                                 double horizontalAmount, double verticalAmount) {
        if (verticalAmount == 0) return false;
        try {
            int height = numberField(screen, "height");
            if (mouseY < LIST_TOP - 4 || mouseY > height - LIST_BOTTOM_MARGIN) return false;
            ModRuntime current = runtime;
            if (current == null) return false;
            List<ModMenuEntry> entries = current.modMenuEntries();
            int visible = visibleRows(height);
            int next = clampScrollOffset(scrollOffset - (int) Math.signum(verticalAmount),
                    entries.size(), visible);
            scrollOffset = next;
            updateConfigButtonPositions(screen, entries, visible);
            return true;
        } catch (Throwable failure) {
            LOGGER.log(Level.FINE, "Could not update VantaLoader scroll position", failure);
            return false;
        }
    }

    static int visibleRows(int height) {
        return Math.max(1, (height - 112) / ROW_HEIGHT);
    }

    static int clampScrollOffset(int requested, int entryCount, int visibleRows) {
        int maximum = Math.max(0, entryCount - Math.max(1, visibleRows));
        return Math.max(0, Math.min(requested, maximum));
    }

    private static void updateConfigButtonPositions(Object screen, List<ModMenuEntry> entries, int visible) {
        Map<String, Object> buttons = CONFIG_BUTTONS.get(screen);
        if (buttons == null || buttons.isEmpty()) return;
        for (int i = 0; i < entries.size(); i++) {
            Object button = buttons.get(entries.get(i).id());
            if (button == null) continue;
            int row = i - scrollOffset;
            int y = row >= 0 && row < visible ? LIST_TOP + row * ROW_HEIGHT + 7 : -1000;
            try {
                setWidgetY(button, y);
            } catch (Exception failure) {
                LOGGER.log(Level.FINE, "Could not reposition mod settings button", failure);
            }
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
            int visible = visibleRows(height);
            scrollOffset = clampScrollOffset(scrollOffset, entries.size(), visible);
            updateConfigButtonPositions(screen, entries, visible);

            fill(graphics, 12, 12, width - 12, height - 12, 0xD9101722);
            fill(graphics, 12, 12, width - 12, 14, 0xFF55A8FF);
            var header = current.modMenuHeader();
            centered(graphics, font, header.title(), width / 2, 26, 0xFFFFFFFF);
            centered(graphics, font, header.subtitle(), width / 2, 43, 0xFFB7C7D9);

            if (entries.isEmpty()) {
                centered(graphics, font, "No mods loaded", width / 2, 112, 0xFFB7C7D9);
            } else {
                int end = Math.min(entries.size(), scrollOffset + visible);
                enableScissor(graphics, 22, LIST_TOP - 4, width - 22, height - LIST_BOTTOM_MARGIN);
                try {
                    for (int index = scrollOffset; index < end; index++) {
                        ModMenuEntry entry = entries.get(index);
                        int row = index - scrollOffset;
                        int y = LIST_TOP + row * ROW_HEIGHT;
                        fill(graphics, 22, y, width - 22, y + 34, 0xA51E2A39);
                        fill(graphics, 22, y, 25, y + 34, 0xFF4B9DEB);
                        fill(graphics, 30, y + 3, 58, y + 31, 0xFF263C54);
                        String initial = entry.name().isBlank() ? "?" :
                                entry.name().substring(0, 1).toUpperCase(java.util.Locale.ROOT);
                        centered(graphics, font, initial, 44, y + 12, 0xFFFFFFFF);
                        draw(graphics, font, entry.name() + "  " + entry.version(), 66, y + 3, 0xFFFFFFFF);
                        String details = entry.description() == null ? "No description provided" : entry.description();
                        if (entry.license() != null) details += "  •  " + entry.license();
                        if (entry.website() != null) details += "  •  " + entry.website();
                        if (entry.sources() != null) details += "  •  Source: " + entry.sources();
                        draw(graphics, font, truncate(details, Math.max(12, (width - 245) / 6)),
                                66, y + 18, 0xFFB7C7D9);
                    }
                } finally {
                    disableScissor(graphics);
                }
                if (entries.size() > visible) {
                    int first = scrollOffset + 1;
                    centered(graphics, font, "Showing " + first + "–" + end + " of " + entries.size()
                                    + "  •  Scroll to browse",
                            width / 2, height - 48, 0xFF8EA3BA);
                }
            }
        } catch (Throwable failure) {
            LOGGER.log(Level.FINE, "VantaLoader screen rendering skipped", failure);
        }
    }

    public static void close(Object screen) {
        CONFIG_BUTTONS.remove(screen);
        scrollOffset = 0;
        setMinecraftScreen(parentScreen);
    }

    private static Object addButton(Object screen, String label, int x, int y, int width, int height, Runnable action)
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
        return button;
    }

    private static void setWidgetY(Object widget, int y) throws Exception {
        Method setter = findMethod(widget.getClass(), "setY", 1);
        setter.setAccessible(true);
        setter.invoke(widget, y);
    }

    private static void enableScissor(Object graphics, int left, int top, int right, int bottom) throws Exception {
        graphics.getClass().getMethod("enableScissor", int.class, int.class, int.class, int.class)
                .invoke(graphics, left, top, right, bottom);
    }

    private static void disableScissor(Object graphics) throws Exception {
        graphics.getClass().getMethod("disableScissor").invoke(graphics);
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
            if (method.getName().equals("drawCenteredString") && method.getParameterCount() == 5
                    && method.getParameterTypes()[1].isInstance(value)) {
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
