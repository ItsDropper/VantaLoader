package dev.itsdropper.vantaloader.api;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/** Snapshot of completed resource reloads and changes that require a game restart. */
public record ResourceReloadReport(List<Entry> entries, List<Path> restartRequired, Instant updatedAt) {
    public ResourceReloadReport {
        entries = List.copyOf(entries);
        restartRequired = restartRequired.stream().map(path -> path.toAbsolutePath().normalize()).distinct().toList();
        java.util.Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public enum Status { RELOADED, FAILED }

    public record Entry(String modId, String registrationId, Status status,
                        List<Path> changedPaths, String message, Instant completedAt) {
        public Entry {
            changedPaths = List.copyOf(changedPaths);
            java.util.Objects.requireNonNull(status, "status");
            java.util.Objects.requireNonNull(completedAt, "completedAt");
        }
    }
}
