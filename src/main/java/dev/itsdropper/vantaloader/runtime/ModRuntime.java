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
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Owns the shared native-mod class loader and entrypoint lifecycle. */
public final class ModRuntime implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger("VantaLoader.Runtime");
    private final Path gameDirectory;
    private final Path configDirectory;
    private final List<LoadedMod> initialized = new ArrayList<>();
    private URLClassLoader classLoader;
    private boolean started;
    private boolean closed;

    public ModRuntime(Path gameDirectory) {
        this.gameDirectory = gameDirectory.toAbsolutePath().normalize();
        this.configDirectory = this.gameDirectory.resolve("config").resolve("vantaloader");
    }

    public void start(List<ModArtifact> orderedMods) throws Exception {
        if (started || closed) throw new IllegalStateException("ModRuntime can only be started once");
        Files.createDirectories(configDirectory);
        URL[] urls = orderedMods.stream().map(artifact -> toUrl(artifact.path())).toArray(URL[]::new);
        classLoader = new URLClassLoader(urls, VantaMod.class.getClassLoader());
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(classLoader);
        try {
            for (ModArtifact artifact : orderedMods) {
                LoadedMod mod = instantiate(artifact);
                initialized.add(mod);
                mod.instance().onInitialize(new DefaultModContext(artifact.descriptor(), gameDirectory,
                        configDirectory.resolve(artifact.descriptor().id())));
                LOGGER.info("Initialized " + mod.id() + " " + artifact.descriptor().version());
            }
            started = true;
        } catch (Throwable failure) {
            rollback(failure);
            if (failure instanceof Exception exception) throw exception;
            if (failure instanceof Error error) throw error;
            throw new IllegalStateException("Mod initialization failed", failure);
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    private LoadedMod instantiate(ModArtifact artifact) throws Exception {
        String id = artifact.descriptor().id();
        String name = artifact.descriptor().mainEntrypoint();
        try {
            Class<?> type = Class.forName(name, true, classLoader);
            if (!VantaMod.class.isAssignableFrom(type)) {
                throw new IllegalArgumentException("Entrypoint " + name + " for mod '" + id
                        + "' does not implement VantaMod");
            }
            VantaMod instance = (VantaMod) type.getDeclaredConstructor().newInstance();
            return new LoadedMod(id, instance);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            throw new IllegalStateException("Constructor for mod '" + id + "' failed: "
                    + cause.getMessage(), cause);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot construct entrypoint '" + name + "' for mod '" + id
                    + "'. A public no-argument constructor is required.", exception);
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
            try { mod.instance().onShutdown(); }
            catch (Throwable failure) {
                original.addSuppressed(new IllegalStateException("Shutdown failed for mod '" + mod.id() + "'", failure));
            }
        }
        initialized.clear();
        closeLoader(original);
        closed = true;
    }

    public void stop() throws Exception {
        if (closed) return;
        Exception aggregate = null;
        for (int i = initialized.size() - 1; i >= 0; i--) {
            LoadedMod mod = initialized.get(i);
            try {
                mod.instance().onShutdown();
                LOGGER.info("Stopped " + mod.id());
            } catch (Exception failure) {
                if (aggregate == null) aggregate = new Exception("One or more mods failed to shut down");
                aggregate.addSuppressed(new IllegalStateException("Shutdown failed for mod '" + mod.id() + "'", failure));
            }
        }
        initialized.clear();
        closeLoader(aggregate);
        closed = true;
        if (aggregate != null) throw aggregate;
    }

    private void closeLoader(Throwable original) {
        if (classLoader == null) return;
        try { classLoader.close(); }
        catch (IOException exception) {
            if (original != null) original.addSuppressed(exception);
            else LOGGER.log(Level.WARNING, "Failed to close mod class loader", exception);
        }
    }

    @Override public void close() throws Exception { stop(); }
    private record LoadedMod(String id, VantaMod instance) { }
}
