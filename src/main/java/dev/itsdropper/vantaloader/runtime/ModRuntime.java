package dev.itsdropper.vantaloader.runtime;

import dev.itsdropper.vantaloader.api.VantaMod;
import dev.itsdropper.vantaloader.api.MinecraftCapabilities;
import dev.itsdropper.vantaloader.api.MinecraftCapability;
import dev.itsdropper.vantaloader.bootstrap.MinecraftHooks;
import dev.itsdropper.vantaloader.discovery.ModArtifact;
import dev.itsdropper.vantaloader.transformation.TransformationService;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Owns dependency-aware native-mod class loaders and entrypoint lifecycle.
 * Each mod sees its own classes, its declared dependencies, and the loader API.
 */
public final class ModRuntime implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger("VantaLoader.Runtime");
    private final Path gameDirectory;
    private final Path configDirectory;
    private final TransformationService transformationService;
    private final MinecraftCapabilities minecraftCapabilities;
    private final List<LoadedMod> initialized = new ArrayList<>();
    private final List<ModClassLoader> classLoaders = new ArrayList<>();
    private final ModEventBus eventBus = new ModEventBus();
    private boolean started;
    private boolean closed;

    public ModRuntime(Path gameDirectory) {
        this(gameDirectory, new TransformationService());
    }

    public ModRuntime(Path gameDirectory, TransformationService transformationService) {
        this(gameDirectory, transformationService, MinecraftCapabilities.none());
    }

    public ModRuntime(Path gameDirectory, TransformationService transformationService,
                      MinecraftCapabilities minecraftCapabilities) {
        this.gameDirectory = gameDirectory.toAbsolutePath().normalize();
        this.configDirectory = this.gameDirectory.resolve("config").resolve("vantaloader");
        this.transformationService = java.util.Objects.requireNonNull(transformationService, "transformationService");
        this.minecraftCapabilities = java.util.Objects.requireNonNull(minecraftCapabilities, "minecraftCapabilities");
    }

    public void start(List<ModArtifact> orderedMods) throws Exception {
        if (started || closed) throw new IllegalStateException("ModRuntime can only be started once");
        validateOrderedMods(orderedMods);
        Files.createDirectories(configDirectory);
        try {
            Map<String, ModClassLoader> loadersById = new java.util.LinkedHashMap<>();
            for (ModArtifact artifact : orderedMods) {
                String id = artifact.descriptor().id();
                List<ClassLoader> dependencies = new ArrayList<>();
                for (String dependencyId : artifact.descriptor().depends().keySet().stream().sorted().toList()) {
                    ModClassLoader dependencyLoader = loadersById.get(dependencyId);
                    if (dependencyLoader == null) {
                        throw new IllegalStateException("Mod '" + id + "' was not ordered after dependency '"
                                + dependencyId + "' or the dependency is missing");
                    }
                    dependencies.add(dependencyLoader);
                }
                ModClassLoader loader = new ModClassLoader(toUrl(artifact.path()),
                        VantaMod.class.getClassLoader(), dependencies);
                classLoaders.add(loader);
                loadersById.put(id, loader);
            }

            if (minecraftCapabilities.supports(MinecraftCapability.CLASS_TRANSFORMATIONS)) {
                dev.itsdropper.vantaloader.mixin.MixinSupport.initialize(
                        new ArrayList<>(classLoaders),
                        orderedMods);
            }

            for (ModArtifact artifact : orderedMods) {
                ModClassLoader loader = loadersById.get(artifact.descriptor().id());
                ModEventBus.Scope eventScope = new ModEventBus.Scope(eventBus);
                LoadedMod mod = instantiate(artifact, loader, eventScope);
                initialized.add(mod);
                invokeInitialize(mod, artifact, eventScope);
                LOGGER.info("Initialized " + mod.id() + " " + artifact.descriptor().version());
            }
            MinecraftHooks.bind(eventBus);
            started = true;
        } catch (Throwable failure) {
            rollback(failure);
            if (failure instanceof Exception exception) throw exception;
            if (failure instanceof Error error) throw error;
            throw new IllegalStateException("Mod initialization failed", failure);
        }
    }

    /** Validates the complete ordered mod set before invoking third-party code. */
    private static void validateOrderedMods(List<ModArtifact> orderedMods) {
        java.util.Objects.requireNonNull(orderedMods, "orderedMods");
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int index = 0; index < orderedMods.size(); index++) {
            ModArtifact artifact = java.util.Objects.requireNonNull(
                    orderedMods.get(index), "orderedMods[" + index + "]");
            var descriptor = java.util.Objects.requireNonNull(
                    artifact.descriptor(), "descriptor for mod artifact at index " + index);
            String id = java.util.Objects.requireNonNull(descriptor.id(), "mod ID");
            if (!seen.add(id)) {
                throw new IllegalArgumentException("Duplicate mod ID in runtime input: '" + id + "'");
            }
            Path path = java.util.Objects.requireNonNull(artifact.path(), "JAR path for mod '" + id + "'")
                    .toAbsolutePath().normalize();
            if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
                throw new IllegalArgumentException("Mod JAR for '" + id
                        + "' is not a readable regular file: " + path);
            }
            for (String dependencyId : descriptor.depends().keySet()) {
                if (id.equals(dependencyId)) {
                    throw new IllegalArgumentException("Mod '" + id + "' cannot depend on itself");
                }
                if (!seen.contains(dependencyId)) {
                    throw new IllegalArgumentException("Mod '" + id + "' must be ordered after dependency '"
                            + dependencyId + "' or the dependency is missing");
                }
            }
        }
    }

    private void invokeInitialize(LoadedMod mod, ModArtifact artifact, ModEventBus.Scope eventScope) throws Exception {
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(mod.classLoader());
        try {
            Path modConfigDirectory = configDirectory.resolve(artifact.descriptor().id());
            Files.createDirectories(modConfigDirectory);
            mod.instance().onInitialize(new DefaultModContext(artifact.descriptor(), gameDirectory,
                    modConfigDirectory, transformationService, eventScope, minecraftCapabilities));
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    private LoadedMod instantiate(ModArtifact artifact, ModClassLoader loader, ModEventBus.Scope eventScope) throws Exception {
        String id = artifact.descriptor().id();
        String name = artifact.descriptor().mainEntrypoint();
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(loader);
        try {
            Class<?> type = Class.forName(name, true, loader);
            if (!VantaMod.class.isAssignableFrom(type)) {
                throw new IllegalArgumentException("Entrypoint " + name + " for mod '" + id
                        + "' does not implement VantaMod");
            }
            VantaMod instance = (VantaMod) type.getConstructor().newInstance();
            return new LoadedMod(id, instance, loader, eventScope, artifact.descriptor());
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            throw new IllegalStateException("Constructor for mod '" + id + "' failed: "
                    + cause.getMessage(), cause);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot construct entrypoint '" + name + "' for mod '" + id
                    + "'. A public class and public no-argument constructor are required.", exception);
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    private static URL toUrl(Path path) {
        try { return path.toUri().toURL(); }
        catch (java.net.MalformedURLException exception) {
            throw new IllegalArgumentException("Invalid mod JAR path: " + path, exception);
        }
    }

    private void rollback(Throwable original) {
        MinecraftHooks.unbind(eventBus);
        Error fatalFailure = null;
        try {
            for (int i = initialized.size() - 1; i >= 0; i--) {
                LoadedMod mod = initialized.get(i);
                try {
                    invokeShutdown(mod, original);
                } catch (VirtualMachineError | ThreadDeath failure) {
                    fatalFailure = failure;
                    break;
                }
            }
        } finally {
            initialized.clear();
            closeLoaders(fatalFailure == null ? original : fatalFailure);
            closed = true;
        }
        if (fatalFailure != null) {
            fatalFailure.addSuppressed(original);
            throw fatalFailure;
        }
    }

    private void invokeShutdown(LoadedMod mod, Throwable aggregate) {
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(mod.classLoader());
        try {
            mod.instance().onShutdown();
        } catch (Throwable failure) {
            if (failure instanceof VirtualMachineError fatal) throw fatal;
            if (failure instanceof ThreadDeath death) throw death;
            aggregate.addSuppressed(new IllegalStateException(
                    "Shutdown failed for mod '" + mod.id() + "'", failure));
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
            mod.eventScope().close();
        }
    }

    public void stop() throws Exception {
        if (closed) return;
        MinecraftHooks.unbind(eventBus);
        Exception aggregate = null;
        Error fatalFailure = null;
        try {
            for (int i = initialized.size() - 1; i >= 0; i--) {
                LoadedMod mod = initialized.get(i);
                try {
                    ClassLoader previous = Thread.currentThread().getContextClassLoader();
                    Thread.currentThread().setContextClassLoader(mod.classLoader());
                    try { mod.instance().onShutdown(); }
                    finally { Thread.currentThread().setContextClassLoader(previous); }
                    LOGGER.info("Stopped " + mod.id());
                } catch (Throwable failure) {
                    if (failure instanceof VirtualMachineError || failure instanceof ThreadDeath) {
                        fatalFailure = (Error) failure;
                        break;
                    }
                    if (aggregate == null) aggregate = new Exception("One or more mods failed to shut down");
                    aggregate.addSuppressed(new IllegalStateException(
                            "Shutdown failed for mod '" + mod.id() + "'", failure));
                } finally {
                    mod.eventScope().close();
                }
            }
        } finally {
            initialized.clear();
            closeLoaders(fatalFailure == null ? aggregate : fatalFailure);
            closed = true;
        }
        if (fatalFailure != null) {
            if (aggregate != null) fatalFailure.addSuppressed(aggregate);
            throw fatalFailure;
        }
        if (aggregate != null) throw aggregate;
    }

    private void closeLoaders(Throwable original) {
        for (int i = classLoaders.size() - 1; i >= 0; i--) {
            try { classLoaders.get(i).close(); }
            catch (IOException exception) {
                if (original != null) original.addSuppressed(exception);
                else LOGGER.log(Level.WARNING, "Failed to close mod class loader", exception);
            }
        }
        classLoaders.clear();
    }

    @Override public void close() throws Exception { stop(); }

    /** Runtime-derived title and loaded-mod count for the in-game mod screen header. */
    public dev.itsdropper.vantaloader.api.ModMenuHeader modMenuHeader() {
        return dev.itsdropper.vantaloader.api.ModMenuHeader.create(
                System.getProperty("vanta.minecraft.version", "unknown"), initialized.size());
    }

    /** Immutable metadata snapshot used by the in-game mod list. */
    public List<dev.itsdropper.vantaloader.api.ModMenuEntry> modMenuEntries() {
        return initialized.stream().map(mod -> {
            var descriptor = mod.descriptor();
            java.net.URL icon = descriptor.icon() == null ? null : mod.classLoader().getResource(descriptor.icon());
            return new dev.itsdropper.vantaloader.api.ModMenuEntry(
                    descriptor.id(), descriptor.name(), descriptor.version().toString(),
                    descriptor.description(), descriptor.license(), descriptor.website(),
                    descriptor.sources(), icon, mod.instance() instanceof dev.itsdropper.vantaloader.api.ModMenuApi);
        }).toList();
    }

    /**
     * Opens a mod's settings through the opt-in public Mod Menu API.
     * Returns the game-specific screen object without linking the loader API to Minecraft classes.
     */
    public Object createModConfigScreen(String modId, Object parentScreen) throws Exception {
        if (closed) throw new IllegalStateException("Mod runtime is closed");
        LoadedMod mod = initialized.stream().filter(candidate -> candidate.id().equals(modId))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown mod: " + modId));
        if (!(mod.instance() instanceof dev.itsdropper.vantaloader.api.ModMenuApi menuApi)) {
            throw new IllegalStateException("Mod '" + modId + "' has not implemented ModMenuApi");
        }
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(mod.classLoader());
        try {
            return menuApi.createConfigScreen(parentScreen);
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    private record LoadedMod(String id, VantaMod instance, ModClassLoader classLoader,
                             ModEventBus.Scope eventScope,
                             dev.itsdropper.vantaloader.metadata.ModDescriptor descriptor) { }

    /** Child-first for mod implementation classes, with explicit dependency visibility. */
    private static final class ModClassLoader extends URLClassLoader {
        private static final String[] PARENT_FIRST = {
                "java.", "javax.", "jdk.", "sun.", "com.sun.",
                "dev.itsdropper.vantaloader.api.", "org.objectweb.asm.", "org.spongepowered.asm."
        };
        private final List<ClassLoader> dependencies;

        private ModClassLoader(URL jar, ClassLoader parent, List<ClassLoader> dependencies) {
            super(new URL[]{jar}, parent);
            this.dependencies = List.copyOf(dependencies);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    if (isParentFirst(name)) {
                        loaded = getParent().loadClass(name);
                    } else {
                        try {
                            loaded = findClass(name);
                        } catch (ClassNotFoundException ownMiss) {
                            for (ClassLoader dependency : dependencies) {
                                try {
                                    loaded = dependency.loadClass(name);
                                    break;
                                } catch (ClassNotFoundException ignored) {
                                    // Continue through only explicitly declared dependencies.
                                }
                            }
                            if (loaded == null) loaded = getParent().loadClass(name);
                        }
                    }
                }
                if (resolve && loaded.getClassLoader() == this) resolveClass(loaded);
                return loaded;
            }
        }

        @Override
        public URL getResource(String name) {
            URL resource = findResource(name);
            if (resource != null) return resource;
            for (ClassLoader dependency : dependencies) {
                resource = dependency.getResource(name);
                if (resource != null) return resource;
            }
            return getParent().getResource(name);
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            LinkedHashSet<URL> resources = new LinkedHashSet<>();
            findResources(name).asIterator().forEachRemaining(resources::add);
            for (ClassLoader dependency : dependencies) {
                dependency.getResources(name).asIterator().forEachRemaining(resources::add);
            }
            getParent().getResources(name).asIterator().forEachRemaining(resources::add);
            return java.util.Collections.enumeration(resources);
        }

        private static boolean isParentFirst(String name) {
            for (String prefix : PARENT_FIRST) if (name.startsWith(prefix)) return true;
            return false;
        }
    }
}
