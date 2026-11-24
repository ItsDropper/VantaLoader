package dev.itsdropper.vantaloader.runtime;

import dev.itsdropper.vantaloader.api.ResourceReloadContext;
import dev.itsdropper.vantaloader.api.ResourceReloadListener;
import dev.itsdropper.vantaloader.api.ResourceReloadPlan;
import dev.itsdropper.vantaloader.api.ResourceReloadReport;
import dev.itsdropper.vantaloader.api.ResourceReloadSubscription;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Watches mod-owned resource trees and coordinates reversible reload transactions. */
final class ResourceReloadService implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger("VantaLoader.Reload");
    private static final long DEBOUNCE_MILLIS = 120;
    private final Path gameDirectory;
    private final Path modsDirectory;
    private final WatchService watchService;
    private final Map<WatchKey, Path> watchDirectories = new HashMap<>();
    private final CopyOnWriteArrayList<Registration> registrations = new CopyOnWriteArrayList<>();
    private final List<ResourceReloadReport.Entry> entries = new ArrayList<>();
    private final Set<Path> restartRequired = new LinkedHashSet<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Thread worker;
    private volatile Instant updatedAt = Instant.now();

    ResourceReloadService(Path gameDirectory) throws IOException {
        this.gameDirectory = gameDirectory.toAbsolutePath().normalize();
        this.modsDirectory = this.gameDirectory.resolve("mods");
        Files.createDirectories(this.modsDirectory);
        this.watchService = FileSystems.getDefault().newWatchService();
        registerTree(this.modsDirectory);
        worker = new Thread(this::watchLoop, "VantaLoader-Resource-Reload");
        worker.setDaemon(true);
        worker.start();
    }

    ResourceReloadSubscription register(String modId, String id, Path directory, ResourceReloadListener listener) {
        if (closed.get()) throw new IllegalStateException("Resource reload service is closed");
        if (id == null || id.isBlank()) throw new IllegalArgumentException("registration id must not be blank");
        Path root = java.util.Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
        if (!root.startsWith(gameDirectory) || root.equals(gameDirectory)) {
            throw new IllegalArgumentException("Resource directory must be inside the game directory: " + root);
        }
        java.util.Objects.requireNonNull(listener, "listener");
        if (registrations.stream().anyMatch(item -> item.modId.equals(modId) && item.id.equals(id))) {
            throw new IllegalArgumentException("Duplicate resource reload registration '" + id + "' for mod '" + modId + "'");
        }
        try {
            Files.createDirectories(root);
            registerTree(root);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot watch resource directory for mod '" + modId + "': " + root, exception);
        }
        Registration registration = new Registration(modId, id, root, listener);
        registrations.add(registration);
        return () -> registrations.remove(registration);
    }

    private void registerTree(Path root) throws IOException {
        if (!Files.isDirectory(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path directory : paths.filter(Files::isDirectory).sorted().toList()) registerDirectory(directory);
        }
    }

    private synchronized void registerDirectory(Path directory) throws IOException {
        Path normalized = directory.toAbsolutePath().normalize();
        if (watchDirectories.containsValue(normalized)) return;
        WatchKey key = normalized.register(watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE);
        watchDirectories.put(key, normalized);
    }

    private void watchLoop() {
        while (!closed.get()) {
            WatchKey key;
            try { key = watchService.take(); }
            catch (InterruptedException interrupted) {
                if (closed.get()) return;
                Thread.currentThread().interrupt();
                return;
            } catch (java.nio.file.ClosedWatchServiceException ignored) { return; }
            Path directory;
            synchronized (this) { directory = watchDirectories.get(key); }
            Set<Path> changed = new LinkedHashSet<>();
            if (directory != null) {
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                        registrations.stream().filter(registration -> directory.startsWith(registration.directory)
                                || registration.directory.startsWith(directory))
                                .forEach(registration -> changed.add(registration.directory));
                        continue;
                    }
                    Path path = directory.resolve((Path) event.context()).toAbsolutePath().normalize();
                    if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(path)) {
                        try { registerTree(path); }
                        catch (IOException failure) {
                            LOGGER.log(Level.WARNING, "Cannot watch new resource directory " + path, failure);
                        }
                    }
                    if (directory.equals(modsDirectory)
                            && path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".jar")) {
                        recordRestartRequired(path);
                    } else if (registrations.stream().anyMatch(registration -> path.startsWith(registration.directory))) {
                        changed.add(path);
                    }
                }
            }
            if (!key.reset()) {
                synchronized (this) { watchDirectories.remove(key); }
            }
            if (!changed.isEmpty()) {
                try { Thread.sleep(DEBOUNCE_MILLIS); }
                catch (InterruptedException interrupted) { if (closed.get()) return; }
                processChanges(changed);
            }
        }
    }

    private void recordRestartRequired(Path path) {
        synchronized (restartRequired) {
            restartRequired.add(path);
            updatedAt = Instant.now();
        }
        LOGGER.warning("Mod JAR changed while the game is running; restart required to apply: " + path);
    }

    private void processChanges(Set<Path> changedPaths) {
        List<Registration> affected = registrations.stream()
                .filter(registration -> changedPaths.stream().anyMatch(path -> path.startsWith(registration.directory)))
                .sorted(Comparator.comparing((Registration item) -> item.modId).thenComparing(item -> item.id))
                .toList();
        if (affected.isEmpty()) return;
        List<Prepared> prepared = new ArrayList<>();
        Instant observed = Instant.now();
        try {
            for (Registration registration : affected) {
                Set<Path> paths = changedPaths.stream().filter(path -> path.startsWith(registration.directory))
                        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
                ResourceReloadContext context = new ResourceReloadContext(registration.directory, paths, observed);
                ResourceReloadPlan plan = java.util.Objects.requireNonNull(
                        registration.listener.prepare(context),
                        "Reload listener returned a null plan: " + registration.id);
                prepared.add(new Prepared(registration, paths, plan));
            }
            for (Prepared item : prepared) item.plan.commit();
            for (Prepared item : prepared) record(item, ResourceReloadReport.Status.RELOADED, "Resources committed");
        } catch (Throwable failure) {
            if (failure instanceof VirtualMachineError fatal) throw fatal;
            if (failure instanceof ThreadDeath death) throw death;
            for (int index = prepared.size() - 1; index >= 0; index--) {
                try { prepared.get(index).plan.rollback(); }
                catch (Throwable rollbackFailure) {
                    if (rollbackFailure instanceof VirtualMachineError fatal) throw fatal;
                    if (rollbackFailure instanceof ThreadDeath death) throw death;
                    failure.addSuppressed(rollbackFailure);
                }
            }
            String message = failure.getClass().getSimpleName() + ": " + String.valueOf(failure.getMessage());
            for (Registration registration : affected) {
                Set<Path> paths = changedPaths.stream().filter(path -> path.startsWith(registration.directory))
                        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
                record(new Prepared(registration, paths, null), ResourceReloadReport.Status.FAILED,
                        "Reload transaction rolled back: " + message);
            }
            LOGGER.log(Level.SEVERE, "Resource reload transaction failed and was rolled back", failure);
        }
    }

    private void record(Prepared prepared, ResourceReloadReport.Status status, String message) {
        synchronized (entries) {
            entries.add(new ResourceReloadReport.Entry(prepared.registration.modId, prepared.registration.id,
                    status, prepared.paths.stream().sorted().toList(), message, Instant.now()));
            if (entries.size() > 200) entries.remove(0);
            updatedAt = Instant.now();
        }
    }

    ResourceReloadReport report() {
        List<ResourceReloadReport.Entry> entrySnapshot;
        synchronized (entries) { entrySnapshot = List.copyOf(entries); }
        List<Path> restartSnapshot;
        synchronized (restartRequired) { restartSnapshot = restartRequired.stream().sorted().toList(); }
        return new ResourceReloadReport(entrySnapshot, restartSnapshot, updatedAt);
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        registrations.clear();
        try { watchService.close(); }
        catch (IOException failure) { LOGGER.log(Level.WARNING, "Failed to close resource watcher", failure); }
        worker.interrupt();
        try { worker.join(2000); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
    }

    private record Registration(String modId, String id, Path directory, ResourceReloadListener listener) { }
    private record Prepared(Registration registration, Set<Path> paths, ResourceReloadPlan plan) { }
}
