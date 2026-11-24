package dev.itsdropper.vantaloader.api;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Set;

/** Immutable description of one coalesced change batch for a mod-owned resource directory. */
public record ResourceReloadContext(Path directory, Set<Path> changedPaths, Instant observedAt) {
    public ResourceReloadContext {
        directory = directory.toAbsolutePath().normalize();
        changedPaths = changedPaths.stream().map(path -> path.toAbsolutePath().normalize())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (changedPaths.isEmpty()) throw new IllegalArgumentException("changedPaths must not be empty");
        java.util.Objects.requireNonNull(observedAt, "observedAt");
    }
}
