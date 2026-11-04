package dev.itsdropper.vantaloader.metadata;

import java.util.Map;

public record ModDescriptor(int schemaVersion, String id, String name, Version version,
                            String mainEntrypoint, Map<String, VersionConstraint> depends) {
    public ModDescriptor {
        depends = Map.copyOf(depends);
    }
}
