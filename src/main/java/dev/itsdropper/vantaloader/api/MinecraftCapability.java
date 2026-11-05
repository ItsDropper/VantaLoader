package dev.itsdropper.vantaloader.api;

/**
 * A loader capability that a mod may require before using an integration.
 * Capabilities describe verified runtime facilities, not Minecraft version guesses.
 */
public enum MinecraftCapability {
    CLASS_TRANSFORMATIONS,
    OFFICIAL_MOJANG_MAPPINGS,
    CLIENT_LIFECYCLE_EVENTS,
    SERVER_LIFECYCLE_EVENTS,
    RESOURCE_RELOAD,
    MIXIN_INTEROP
}
