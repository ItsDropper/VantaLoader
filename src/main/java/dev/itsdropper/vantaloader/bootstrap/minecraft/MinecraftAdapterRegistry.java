package dev.itsdropper.vantaloader.bootstrap.minecraft;

import java.util.Map;
import java.util.Optional;

/** Exact-version adapter registry. Unknown versions deliberately receive no fallback. */
public final class MinecraftAdapterRegistry {
    private static final Map<String, MinecraftAdapter> ADAPTERS = Map.of(
            "1.21.11", new Minecraft12111Adapter()
    );
    private MinecraftAdapterRegistry() { }
    public static Optional<MinecraftAdapter> find(String minecraftVersion) {
        if (minecraftVersion == null || minecraftVersion.isBlank()) return Optional.empty();
        return Optional.ofNullable(ADAPTERS.get(minecraftVersion));
    }
}
