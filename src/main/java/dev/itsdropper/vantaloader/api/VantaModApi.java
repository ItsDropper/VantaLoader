package dev.itsdropper.vantaloader.api;

/**
 * Vanta Mod API identity and compatibility helpers.
 *
 * <p>The API version is independent of the loader implementation version. Mods
 * should compile against the matching VantaLoader-api.jar and use runtime
 * capability negotiation for version-specific game integrations.</p>
 */
public final class VantaModApi {
    public static final int VERSION_MAJOR = 1;
    public static final int VERSION_MINOR = 1;
    public static final int VERSION_PATCH = 0;
    public static final String VERSION = VERSION_MAJOR + "." + VERSION_MINOR + "." + VERSION_PATCH;

    private VantaModApi() { }

    /** Throws with an actionable list when the active adapter lacks a required facility. */
    public static void requireCapabilities(ModContext context, MinecraftCapability... required) {
        java.util.Objects.requireNonNull(context, "context");
        java.util.Objects.requireNonNull(required, "required");
        java.util.List<MinecraftCapability> missing = java.util.Arrays.stream(required)
                .map(capability -> java.util.Objects.requireNonNull(capability, "capability"))
                .filter(capability -> !context.minecraftCapabilities().supports(capability))
                .toList();
        if (!missing.isEmpty()) {
            throw new UnsupportedOperationException("Vanta Mod API " + VERSION
                    + " requires unavailable Minecraft capabilities: " + missing);
        }
    }
}
