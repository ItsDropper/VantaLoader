package dev.itsdropper.vantaloader.runtime;

import dev.itsdropper.vantaloader.api.VantaMod;
import dev.itsdropper.vantaloader.api.MinecraftCapabilities;
import dev.itsdropper.vantaloader.api.MinecraftCapability;
import dev.itsdropper.vantaloader.bootstrap.MinecraftHooks;
import dev.itsdropper.vantaloader.discovery.ModArtifact;
import dev.itsdropper.vantaloader.transformation.TransformationService;
import dev.itsdropper.vantaloader.mapping.MinecraftMappings;
import dev.itsdropper.vantaloader.mapping.MinecraftClassRemapper;

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
    private final MinecraftMappings minecraftMappings;
    private final List<LoadedMod> initialized = new ArrayList<>();
    private final List<ModClassLoader> classLoaders = new ArrayList<>();
    private final ModEventBus eventBus = new ModEventBus();
    private final ModServiceRegistry serviceRegistry = new ModServiceRegistry();
    private final ModClientCommandService clientCommandService;
    private ResourceReloadService resourceReloadService;
    private dev.itsdropper.vantaloader.api.ResourceReloadReport lastReloadReport;
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
        this(gameDirectory, transformationService, minecraftCapabilities, null);
    }

    public ModRuntime(Path gameDirectory, TransformationService transformationService,
                      MinecraftCapabilities minecraftCapabilities, MinecraftMappings minecraftMappings) {
        this.gameDirectory = gameDirectory.toAbsolutePath().normalize();
        this.configDirectory = this.gameDirectory.resolve("config").resolve("vantaloader");
        this.transformationService = java.util.Objects.requireNonNull(transformationService, "transformationService");
        this.minecraftCapabilities = java.util.Objects.requireNonNull(minecraftCapabilities, "minecraftCapabilities");
        this.minecraftMappings = minecraftMappings;
        this.clientCommandService = new ModClientCommandService(
                minecraftCapabilities.supports(MinecraftCapability.CLIENT_COMMANDS));
    }

    public void start(List<ModArtifact> orderedMods) throws Exception {
        if (started || closed) throw new IllegalStateException("ModRuntime can only be started once");
        validateOrderedMods(orderedMods);
        Files.createDirectories(configDirectory);
        try {
            resourceReloadService = new ResourceReloadService(gameDirectory);
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
                        VantaMod.class.getClassLoader(), dependencies, minecraftMappings);
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
                ModServiceRegistry.Scope serviceScope = serviceRegistry.createScope(artifact.descriptor().id());
                ModClientCommandService.Scope commandScope = clientCommandService.createScope(artifact.descriptor().id());
                JsonModConfig modConfig;
                try {
                    Path modConfigDirectory = configDirectory.resolve(artifact.descriptor().id());
                    Files.createDirectories(modConfigDirectory);
                    modConfig = new JsonModConfig(modConfigDirectory.resolve("vma-config.json"));
                } catch (Exception | Error failure) {
                    eventScope.close();
                    serviceScope.close();
                    commandScope.close();
                    throw failure;
                }
                ModKeyBindingRegistry keyBindings = new ModKeyBindingRegistry(eventScope,
                        minecraftCapabilities.supports(MinecraftCapability.KEYBOARD_INPUT_EVENTS), modConfig);
                LoadedMod mod;
                try {
                    mod = instantiate(artifact, loader, eventScope, serviceScope, keyBindings, modConfig, commandScope);
                } catch (Exception | Error failure) {
                    keyBindings.close();
                    eventScope.close();
                    serviceScope.close();
                    commandScope.close();
                    throw failure;
                }
                try {
                    invokeInitialize(mod, artifact, eventScope);
                    initialized.add(mod);
                } catch (Exception | Error failure) {
                    mod.keyBindings().close();
                    eventScope.close();
                    mod.resourceScope().close();
                    mod.tasks().close();
                    mod.serviceScope().close();
                    mod.commandScope().close();
                    mod.commandScope().close();
                    throw failure;
                }
                LOGGER.info("Initialized " + mod.id() + " " + artifact.descriptor().version());
            }
            MinecraftHooks.bind(eventBus, clientCommandService);
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
            mod.instance().onInitialize(new DefaultModContext(artifact.descriptor(), gameDirectory,
                    modConfigDirectory, transformationService, eventScope, minecraftCapabilities,
                    mod.resourceScope(), mod.config(), mod.tasks(), mod.serviceScope(), mod.keyBindings(), mod.commandScope()));
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    private LoadedMod instantiate(ModArtifact artifact, ModClassLoader loader, ModEventBus.Scope eventScope,
                                  ModServiceRegistry.Scope serviceScope,
                                  ModKeyBindingRegistry keyBindings, JsonModConfig modConfig,
                                  ModClientCommandService.Scope commandScope) throws Exception {
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
            return new LoadedMod(id, instance, loader, eventScope,
                    new ModResourceReloadScope(id, loader, resourceReloadService), artifact.descriptor(),
                    new ModTaskService(id, loader), serviceScope, keyBindings, modConfig, commandScope);
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
        closeResourceReloadService(original);
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
            mod.keyBindings().close();
            mod.eventScope().close();
            mod.resourceScope().close();
            mod.tasks().close();
            mod.serviceScope().close();
        }
    }

    public void stop() throws Exception {
        if (closed) return;
        MinecraftHooks.unbind(eventBus);
        Exception aggregate = new Exception("One or more mods failed to shut down");
        Error fatalFailure = null;
        try {
            try {
                closeResourceReloadService(null);
            } catch (Throwable failure) {
                if (failure instanceof VirtualMachineError || failure instanceof ThreadDeath) fatalFailure = (Error) failure;
                else aggregate.addSuppressed(new IllegalStateException("Failed to stop resource reload service", failure));
            }
            for (int i = initialized.size() - 1; i >= 0; i--) {
                LoadedMod mod = initialized.get(i);
                try {
                    ClassLoader previous = Thread.currentThread().getContextClassLoader();
                    Thread.currentThread().setContextClassLoader(mod.classLoader());
                    try {
                        mod.instance().onShutdown();
                        LOGGER.info("Stopped " + mod.id());
                    } finally {
                        Thread.currentThread().setContextClassLoader(previous);
                    }
                } catch (Throwable failure) {
                    if (failure instanceof VirtualMachineError || failure instanceof ThreadDeath) {
                        if (fatalFailure == null) fatalFailure = (Error) failure;
                        else fatalFailure.addSuppressed(failure);
                    } else if (fatalFailure != null) {
                        fatalFailure.addSuppressed(new IllegalStateException("Shutdown failed for mod '" + mod.id() + "'", failure));
                    } else {
                        aggregate.addSuppressed(new IllegalStateException("Shutdown failed for mod '" + mod.id() + "'", failure));
                    }
                } finally {
                    AutoCloseable[] owned = {mod.keyBindings(), mod.commandScope(), mod.eventScope(), mod.resourceScope(), mod.tasks(), mod.serviceScope()};
                    for (AutoCloseable resource : owned) {
                        try { resource.close(); }
                        catch (Throwable failure) {
                            if (failure instanceof VirtualMachineError || failure instanceof ThreadDeath) {
                                if (fatalFailure == null) fatalFailure = (Error) failure;
                                else fatalFailure.addSuppressed(failure);
                            } else if (fatalFailure != null) {
                                fatalFailure.addSuppressed(new IllegalStateException("Failed to close resources for mod '" + mod.id() + "'", failure));
                            } else {
                                aggregate.addSuppressed(new IllegalStateException("Failed to close resources for mod '" + mod.id() + "'", failure));
                            }
                        }
                    }
                }
            }
        } finally {
            initialized.clear();
            closeLoaders(fatalFailure != null ? fatalFailure : aggregate.getSuppressed().length == 0 ? null : aggregate);
            closed = true;
        }
        if (fatalFailure != null) {
            if (aggregate.getSuppressed().length > 0) fatalFailure.addSuppressed(aggregate);
            throw fatalFailure;
        }
        if (aggregate.getSuppressed().length > 0) throw aggregate;
    }

    private void closeResourceReloadService(Throwable original) {
        if (resourceReloadService == null) return;
        resourceReloadService.close();
        lastReloadReport = resourceReloadService.report();
        resourceReloadService = null;
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
        clientCommandService.close();
    }

    @Override public void close() throws Exception { stop(); }

    /** Latest reload results and class-change restart requirements. */
    public dev.itsdropper.vantaloader.api.ResourceReloadReport resourceReloadReport() {
        if (resourceReloadService != null) return resourceReloadService.report();
        if (lastReloadReport != null) return lastReloadReport;
        return new dev.itsdropper.vantaloader.api.ResourceReloadReport(List.of(), List.of(), java.time.Instant.now());
    }

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
                             ModEventBus.Scope eventScope, ModResourceReloadScope resourceScope,
                             dev.itsdropper.vantaloader.metadata.ModDescriptor descriptor,
                             ModTaskService tasks, ModServiceRegistry.Scope serviceScope,
                             ModKeyBindingRegistry keyBindings, JsonModConfig config,
                             ModClientCommandService.Scope commandScope) { }

    /** Child-first for mod implementation classes, with explicit dependency visibility. */
    private static final class ModClassLoader extends URLClassLoader {
        private static final String[] PARENT_FIRST = {
                "java.", "javax.", "jdk.", "sun.", "com.sun.",
                "dev.itsdropper.vantaloader.api.", "org.objectweb.asm.", "org.spongepowered.asm."
        };
        private final List<ClassLoader> dependencies;
        private final MinecraftMappings mappings;
        private final dev.itsdropper.vantaloader.mapping.IntermediaryMappings intermediaryMappings;

        private ModClassLoader(URL jar, ClassLoader parent, List<ClassLoader> dependencies,
                               MinecraftMappings mappings) {
            super(new URL[]{jar}, parent);
            this.dependencies = List.copyOf(dependencies);
            this.mappings = mappings;
            this.intermediaryMappings = mappings == null ? null : loadIntermediaryMappings();
        }

        private dev.itsdropper.vantaloader.mapping.IntermediaryMappings loadIntermediaryMappings() {
            try (java.io.InputStream input = super.getResourceAsStream("mappings/mappings.tiny")) {
                if (input == null) return null;
                return dev.itsdropper.vantaloader.mapping.IntermediaryMappings.read(input);
            } catch (IOException exception) {
                LOGGER.log(Level.WARNING, "Cannot load bundled intermediary mappings for Mixin refmaps", exception);
                return null;
            }
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            String resourceName = name.replace('.', '/') + ".class";
            URL resource = findResource(resourceName);
            if (resource == null) throw new ClassNotFoundException(name);
            try (java.io.InputStream input = resource.openStream()) {
                byte[] bytecode = input.readAllBytes();
                if (mappings != null) bytecode = MinecraftClassRemapper.remap(bytecode, mappings);

                int packageSeparator = name.lastIndexOf('.');
                if (packageSeparator > 0) {
                    String packageName = name.substring(0, packageSeparator);
                    synchronized (this) {
                        if (getDefinedPackage(packageName) == null) {
                            try {
                                definePackage(packageName, null, null, null, null, null, null, null);
                            } catch (IllegalArgumentException alreadyDefined) {
                                if (getDefinedPackage(packageName) == null) throw alreadyDefined;
                            }
                        }
                    }
                }
                URL codeSourceUrl = resource;
                java.net.URLConnection connection = resource.openConnection();
                if (connection instanceof java.net.JarURLConnection jarConnection) {
                    codeSourceUrl = jarConnection.getJarFileURL();
                }
                java.security.CodeSource codeSource = new java.security.CodeSource(
                        codeSourceUrl, (java.security.cert.Certificate[]) null);
                java.security.ProtectionDomain protectionDomain = new java.security.ProtectionDomain(
                        codeSource, null, this, null);
                return defineClass(name, bytecode, 0, bytecode.length, protectionDomain);
            } catch (IOException exception) {
                throw new ClassNotFoundException("Cannot read mod class " + name + " from " + resource, exception);
            }
        }

        @Override
        public java.io.InputStream getResourceAsStream(String name) {
            if (mappings != null && intermediaryMappings != null && name.endsWith(".refmap.json")) {
                URL resource = findResource(name);
                if (resource != null) {
                    try (java.io.InputStream input = resource.openStream()) {
                        byte[] remapped = intermediaryMappings.remapRefmap(input.readAllBytes(), mappings);
                        return new java.io.ByteArrayInputStream(remapped);
                    } catch (IOException exception) {
                        LOGGER.log(Level.SEVERE, "Cannot remap Mixin reference map " + name, exception);
                        return null;
                    }
                }
            }
            if (mappings != null && name.endsWith(".class")) {
                URL resource = findResource(name);
                if (resource != null) {
                    try (java.io.InputStream input = resource.openStream()) {
                        byte[] bytecode = MinecraftClassRemapper.remap(input.readAllBytes(), mappings);
                        return new java.io.ByteArrayInputStream(bytecode);
                    } catch (IOException exception) {
                        LOGGER.log(Level.WARNING, "Cannot remap mod class resource " + name, exception);
                        return null;
                    }
                }
            }
            return super.getResourceAsStream(name);
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
