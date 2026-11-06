package dev.itsdropper.vantaloader.mixin.client;

import dev.itsdropper.vantaloader.api.ModMenuEntry;
import dev.itsdropper.vantaloader.mapping.MinecraftMappings;
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
    private static volatile MinecraftMappings mappings;
    private static volatile Object parentScreen;
    private static volatile Class<?> generatedScreenClass;
    private static volatile int scrollOffset;
    private static final Map<Object, Map<String, Object>> CONFIG_BUTTONS =
            java.util.Collections.synchronizedMap(new WeakHashMap<>());

    private VantaMenuHooks() { }

    public static void bind(ModRuntime modRuntime, MinecraftMappings minecraftMappings) {
        runtime = Objects.requireNonNull(modRuntime, "modRuntime");
        mappings = minecraftMappings;
    }

    public static void addMenuButton(Object hostScreen) {
        try {
            int width = numberField(hostScreen, "width");
            int height = numberField(hostScreen, "height");
            if (isScreen(hostScreen, "net.minecraft.client.gui.screens.TitleScreen")) {
                addTitleMenuButton(hostScreen);
            } else {
                addButton(hostScreen, "Mods", width - 112, height - 28, 104, 20,
                        () -> openMenu(hostScreen));
            }
        } catch (Throwable failure) {
            LOGGER.log(Level.WARNING, "Could not add VantaLoader button to " + hostScreen.getClass().getName(), failure);
        }
    }

    private static void openMenu(Object parent) {
        try {
            parentScreen = parent;
            Class<?> screenType = generatedScreenClass();
            Class<?> componentType = minecraftClass("net.minecraft.network.chat.Component");
            Object title = literalComponent("Mods");
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
            String screenName = "net.minecraft.client.gui.screens.Screen";
            Class<?> base = minecraftClass(screenName);
            Class<?> componentClass = minecraftClass("net.minecraft.network.chat.Component");
            Class<?> graphicsClass = minecraftClass("net.minecraft.client.gui.GuiGraphics");
            String internalName = base.getPackageName().isEmpty() ? "VantaLoaderGeneratedScreen"
                    : base.getPackageName().replace('.', '/') + "/VantaLoaderGeneratedScreen";
            String hooks = Type.getInternalName(VantaMenuHooks.class);
            String component = Type.getInternalName(componentClass);
            String graphics = Type.getInternalName(graphicsClass);
            String baseInternal = Type.getInternalName(base);
            String initName = runtimeMethod(screenName, "init", "()V");
            String renderName = runtimeMethod(screenName, "render",
                    "(Lnet/minecraft/client/gui/GuiGraphics;IIF)V");
            String scrollName = runtimeMethod("net.minecraft.client.gui.components.events.AbstractContainerEventHandler",
                    "mouseScrolled", "(DDDD)Z");
            if (scrollName.equals("mouseScrolled")) {
                scrollName = runtimeMethod("net.minecraft.client.gui.components.events.GuiEventListener",
                        "mouseScrolled", "(DDDD)Z");
            }
            String closeName = runtimeMethod(screenName, "onClose", "()V");

            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER,
                    internalName, null, baseInternal, null);

            MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>",
                    "(L" + component + ";)V", null, null);
            constructor.visitCode();
            constructor.visitVarInsn(Opcodes.ALOAD, 0);
            constructor.visitVarInsn(Opcodes.ALOAD, 1);
            constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, baseInternal,
                    "<init>", "(L" + component + ";)V", false);
            constructor.visitInsn(Opcodes.RETURN);
            constructor.visitMaxs(0, 0);
            constructor.visitEnd();

            MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, initName, "()V", null, null);
            init.visitCode();
            init.visitVarInsn(Opcodes.ALOAD, 0);
            init.visitMethodInsn(Opcodes.INVOKESPECIAL, baseInternal, initName, "()V", false);
            init.visitVarInsn(Opcodes.ALOAD, 0);
            init.visitMethodInsn(Opcodes.INVOKESTATIC, hooks, "initialize", "(Ljava/lang/Object;)V", false);
            init.visitInsn(Opcodes.RETURN);
            init.visitMaxs(0, 0);
            init.visitEnd();

            String renderDescriptor = "(L" + graphics + ";IIF)V";
            MethodVisitor render = writer.visitMethod(Opcodes.ACC_PUBLIC, renderName,
                    renderDescriptor, null, null);
            render.visitCode();
            render.visitVarInsn(Opcodes.ALOAD, 0);
            render.visitVarInsn(Opcodes.ALOAD, 1);
            render.visitVarInsn(Opcodes.ILOAD, 2);
            render.visitVarInsn(Opcodes.ILOAD, 3);
            render.visitVarInsn(Opcodes.FLOAD, 4);
            render.visitMethodInsn(Opcodes.INVOKESPECIAL, baseInternal, renderName,
                    renderDescriptor, false);
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

            MethodVisitor scroll = writer.visitMethod(Opcodes.ACC_PUBLIC, scrollName, "(DDDD)Z", null, null);
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

            MethodVisitor close = writer.visitMethod(Opcodes.ACC_PUBLIC, closeName, "()V", null, null);
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
            addButton(screen, "Done", width / 2 - 100, height - 30, 200, 20,
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

    private static Object addButton(Object screen, String label, int x, int y, int width, int height,
                                    Runnable action) throws Exception {
        Class<?> componentType = minecraftClass("net.minecraft.network.chat.Component");
        Class<?> buttonType = minecraftClass("net.minecraft.client.gui.components.Button");
        Class<?> pressType = minecraftClass("net.minecraft.client.gui.components.Button$OnPress");
        Object component = literalComponent(label);
        Object press = Proxy.newProxyInstance(buttonType.getClassLoader(), new Class<?>[]{pressType},
                (proxy, method, args) -> {
                    if (method.getParameterCount() == 1 && method.getReturnType() == void.class) {
                        if ("VantaLoader".equals(label)) LOGGER.info("VantaLoader menu button activated");
                        action.run();
                    }
                    return null;
                });
        String buttonName = "net.minecraft.client.gui.components.Button";
        String builderDescriptor = "(Lnet/minecraft/network/chat/Component;"
                + "Lnet/minecraft/client/gui/components/Button$OnPress;)"
                + "Lnet/minecraft/client/gui/components/Button$Builder;";
        Method builderMethod = mappedMethod(buttonType, buttonName, "builder", builderDescriptor,
                2, componentType, pressType);
        Object builder = builderMethod.invoke(null, component, press);
        String builderName = "net.minecraft.client.gui.components.Button$Builder";
        String boundsDescriptor = "(IIII)Lnet/minecraft/client/gui/components/Button$Builder;";
        mappedMethod(builder.getClass(), builderName, "bounds", boundsDescriptor,
                4, int.class, int.class, int.class, int.class).invoke(builder, x, y, width, height);
        Object button = mappedMethod(builder.getClass(), builderName, "build",
                "()Lnet/minecraft/client/gui/components/Button;", 0).invoke(builder);

        Class<?> listenerType = minecraftClass("net.minecraft.client.gui.components.events.GuiEventListener");
        String screenName = "net.minecraft.client.gui.screens.Screen";
        String addDescriptor = "(Lnet/minecraft/client/gui/components/events/GuiEventListener;)"
                + "Lnet/minecraft/client/gui/components/events/GuiEventListener;";
        mappedMethod(minecraftClass(screenName), screenName, "addRenderableWidget", addDescriptor,
                1, listenerType).invoke(screen, button);
        return button;
    }

    private static void setWidgetY(Object widget, int y) throws Exception {
        String owner = "net.minecraft.client.gui.components.AbstractWidget";
        mappedMethod(minecraftClass(owner), owner, "setY", "(I)V", 1, int.class).invoke(widget, y);
    }

    private static void enableScissor(Object graphics, int left, int top, int right, int bottom) throws Exception {
        String owner = "net.minecraft.client.gui.GuiGraphics";
        mappedMethod(minecraftClass(owner), owner, "enableScissor", "(IIII)V",
                4, int.class, int.class, int.class, int.class).invoke(graphics, left, top, right, bottom);
    }

    private static void disableScissor(Object graphics) throws Exception {
        mappedMethod(minecraftClass("net.minecraft.client.gui.GuiGraphics"), "net.minecraft.client.gui.GuiGraphics",
                "disableScissor", "()V", 0).invoke(graphics);
    }

    private static void setMinecraftScreen(Object target) {
        if (target == null) return;
        try {
            String owner = "net.minecraft.client.Minecraft";
            Class<?> minecraft = minecraftClass(owner);
            Object instance = mappedMethod(minecraft, owner, "getInstance",
                    "()Lnet/minecraft/client/Minecraft;", 0).invoke(null);
            mappedMethod(minecraft, owner, "setScreen",
                    "(Lnet/minecraft/client/gui/screens/Screen;)V", 1,
                    minecraftClass("net.minecraft.client.gui.screens.Screen")).invoke(instance, target);
        } catch (Throwable failure) {
            LOGGER.log(Level.WARNING, "Could not switch Minecraft screens", failure);
        }
    }

    private static Object literalComponent(String value) throws Exception {
        Class<?> component = minecraftClass("net.minecraft.network.chat.Component");
        return mappedMethod(component, "net.minecraft.network.chat.Component", "literal",
                "(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;", 1,
                String.class).invoke(null, value);
    }

    private static void fill(Object graphics, int x1, int y1, int x2, int y2, int color) throws Exception {
        mappedMethod(minecraftClass("net.minecraft.client.gui.GuiGraphics"), "net.minecraft.client.gui.GuiGraphics", "fill",
                "(IIIII)V", 5, int.class, int.class, int.class, int.class, int.class)
                .invoke(graphics, x1, y1, x2, y2, color);
    }

    private static void centered(Object graphics, Object font, String text, int x, int y, int color)
            throws Exception {
        Object value = literalComponent(text);
        mappedMethod(minecraftClass("net.minecraft.client.gui.GuiGraphics"), "net.minecraft.client.gui.GuiGraphics", "drawCenteredString",
                "(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V",
                5, minecraftClass("net.minecraft.client.gui.Font"),
                minecraftClass("net.minecraft.network.chat.Component"),
                int.class, int.class, int.class).invoke(graphics, font, value, x, y, color);
    }

    private static void draw(Object graphics, Object font, String text, int x, int y, int color)
            throws Exception {
        Object value = literalComponent(text);
        mappedMethod(minecraftClass("net.minecraft.client.gui.GuiGraphics"), "net.minecraft.client.gui.GuiGraphics", "drawString",
                "(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V",
                5, minecraftClass("net.minecraft.client.gui.Font"),
                minecraftClass("net.minecraft.network.chat.Component"),
                int.class, int.class, int.class).invoke(graphics, font, value, x, y, color);
    }

    private static int numberField(Object target, String name) throws Exception {
        return ((Number) field(target, name, "I").get(target)).intValue();
    }

    private static Object objectField(Object target, String name) throws Exception {
        return field(target, name, "Lnet/minecraft/client/gui/Font;").get(target);
    }

    private static Field field(Object target, String name, String descriptor) throws Exception {
        String owner = "net.minecraft.client.gui.screens.Screen";
        String runtimeName = mappings == null ? name : mappings.toRuntimeFieldName(owner, name, descriptor);
        Class<?> mappedOwner = minecraftClass(owner);
        for (Class<?> current = target.getClass(); current != null; current = current.getSuperclass()) {
            if (current != mappedOwner && !mappedOwner.isAssignableFrom(current)) continue;
            try {
                Field field = current.getDeclaredField(runtimeName);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(mappedOwner.getName() + "." + runtimeName);
    }

    private static Class<?> minecraftClass(String namedBinaryName) throws ClassNotFoundException {
        String runtimeName = mappings == null ? namedBinaryName : mappings.toRuntimeClassName(namedBinaryName);
        return Class.forName(runtimeName);
    }

    private static String runtimeMethod(String owner, String name, String descriptor) {
        return mappings == null ? name : mappings.toRuntimeMethodName(owner, name, descriptor);
    }

    private static Method mappedMethod(Class<?> runtimeOwner, String namedOwner, String namedName,
                                       String descriptor, int parameterCount, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        String runtimeName = runtimeMethod(namedOwner, namedName, descriptor);
        for (Class<?> current = runtimeOwner; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (!method.getName().equals(runtimeName) || method.getParameterCount() != parameterCount) continue;
                if (parameterTypes.length > 0
                        && !java.util.Arrays.equals(method.getParameterTypes(), parameterTypes)) continue;
                method.setAccessible(true);
                return method;
            }
        }
        for (Method method : runtimeOwner.getMethods()) {
            if (!method.getName().equals(runtimeName) || method.getParameterCount() != parameterCount) continue;
            if (parameterTypes.length > 0
                    && !java.util.Arrays.equals(method.getParameterTypes(), parameterTypes)) continue;
            method.setAccessible(true);
            return method;
        }
        throw new NoSuchMethodException(runtimeOwner.getName() + "." + runtimeName + descriptor);
    }

    private static String truncate(String value, int max) {
        if (value.length() <= max) return value;
        return value.substring(0, Math.max(0, max - 1)) + "…";
    }
}
