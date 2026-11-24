package dev.itsdropper.vantaloader.api;

import java.nio.file.Path;

/** Mod-scoped registration for resources that can be replaced without redefining classes. */
public interface ResourceReloadRegistry {
    /**
     * Watches a directory inside the game directory. Changes are coalesced and delivered
     * on VantaLoader's reload worker. IDs must be unique within the owning mod.
     */
    ResourceReloadSubscription register(String id, Path directory, ResourceReloadListener listener);
}
