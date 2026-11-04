package dev.itsdropper.vantaloader.runtime;

import dev.itsdropper.vantaloader.api.VantaMod;
import dev.itsdropper.vantaloader.discovery.ModArtifact;

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
    private final List<LoadedMod> initialized = new ArrayList<>();
    private final List<ModClassLoader> classLoaders = new ArrayList<>();
    private boolean started;
    private boolean closed;

    public ModRuntime(Path gameDirectory) {
        this.gameDirectory = gameDirectory.toAbsolutePath().normalize();
        this.configDirectory = this.gameDirectory.resolve("config").resolve("vantaloader");
    }

    public void start(List<ModArtifact> orderedMods) throws Exception {
        if (started || closed) throw new IllegalStateException("ModRuntime can only be started once");
        Files.createDirectories(configDirectory);
        try {
            Map<String, ModClassLoader> loadersById = new java.util.LinkedHashMap<>();
            for (ModArtifact artifact : orderedMods) {
                String id = artifact.descriptor().id();
                List<ClassLoader> dependencies = new ArrayList<>();
                for (String dependencyId : artifact.descriptor().depends().keySet()) {
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

            for (ModArtifact artifact : orderedMods) {
                ModClassLoader loader = loadersById.get(artifact.descriptor().id());
                LoadedMod mod = instantiate(artifact, loader);
                initialized.add(mod);
                invokeInitialize(mod, artifact);
                LOGGER.info("Initialized " + mod.id() + " " + artifact.descriptor().version());
            }
            started = true;
        } catch (Throwable failure) {
            rollback(failure);
            if (failure instanceof Exception exception) throw exception;
            if (failure instanceof Error error) throw error;
            throw new IllegalStateException("Mod initialization failed", failure);
        }
    }

    private void invokeInitialize(LoadedMod mod, ModArtifact artifact) throws Exception {
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(mod.classLoader());
        try {
            mod.instance().onInitialize(new DefaultModContext(artifact.descriptor(), gameDirectory,
                    configDirectory.resolve(artifact.descriptor().id())));
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    private LoadedMod instantiate(ModArtifact artifact, ModClassLoader loader) throws Exception {
        String id = artifact.descriptor().id();
        String name = artifact.descriptor().mainEntrypoint();
        try {
            Class<?> type = Class.forName(name, true, loader);
            if (!VantaMod.class.isAssignableFrom(type)) {
                throw new IllegalArgumentException("Entrypoint " + name + " for mod '" + id
                        + "' does not implement VantaMod");
            }
            VantaMod instance = (VantaMod) type.getConstructor().newInstance();
            return new LoadedMod(id, instance, loader);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            throw new IllegalStateException("Constructor for mod '" + id + "' failed: "
                    + cause.getMessage(), cause);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot construct entrypoint '" + name + "' for mod '" + id
                    + "'. A public class and public no-argument constructor are required.", exception);
        }
    }

    private static URL toUrl(Path path) {
        try { return path.toUri().toURL(); }
        catch (java.net.MalformedURLException exception) {
            throw new IllegalArgumentException("Invalid mod JAR path: " + path, exception);
        }
    }

    private void rollback(Throwable original) {
        for (int i = initialized.size() - 1; i >= 0; i--) {
            LoadedMod mod = initialized.get(i);
            invokeShutdown(mod, original);
        }
        initialized.clear();
        closeLoaders(original);
        closed = true;
    }

    private void invokeShutdown(LoadedMod mod, Throwable aggregate) {
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(mod.classLoader());
        try {
            mod.instance().onShutdown();
        } catch (Throwable failure) {
            aggregate.addSuppressed(new IllegalStateException(
                    "Shutdown failed for mod '" + mod.id() + "'", failure));
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    public void stop() throws Exception {
        if (closed) return;
        Exception aggregate = null;
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
                    if (aggregate == null) aggregate = new Exception("One or more mods failed to shut down");
                    aggregate.addSuppressed(new IllegalStateException(
                            "Shutdown failed for mod '" + mod.id() + "'", failure));
                }
            }
        } finally {
            initialized.clear();
            closeLoaders(aggregate);
            closed = true;
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

    private record LoadedMod(String id, VantaMod instance, ModClassLoader classLoader) { }

    /**
     * Child-first for mod implementation classes, with explicit dependency visibility.
     * The Java platform and VantaLoader API remain parent-first to preserve type identity.
     */
    private static final class ModClassLoader extends URLClassLoader {
        private static final String[] PARENT_FIRST = {
                "java.", "javax.", "jdk.", "sun.", "com.sun.",
                "dev.itsdropper.vantaloader.api."
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
                if (resolve) resolveClass(loaded);
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
