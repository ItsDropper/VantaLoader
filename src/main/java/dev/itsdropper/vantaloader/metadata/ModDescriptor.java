package dev.itsdropper.vantaloader.metadata;

import java.util.List;
import java.util.Map;

public record ModDescriptor(int schemaVersion, String id, String name, Version version,
                            String mainEntrypoint, String description, String icon,
                            String license, String website, String sources,
                            List<String> mixins,
                            Map<String, VersionConstraint> depends,
                            Map<String, VersionConstraint> recommends,
                            Map<String, VersionConstraint> suggests,
                            Map<String, VersionConstraint> conflicts,
                            Map<String, VersionConstraint> breaks) {
    public ModDescriptor {
        mixins = List.copyOf(mixins);
        depends = Map.copyOf(depends);
        recommends = Map.copyOf(recommends);
        suggests = Map.copyOf(suggests);
        conflicts = Map.copyOf(conflicts);
        breaks = Map.copyOf(breaks);
    }
}
