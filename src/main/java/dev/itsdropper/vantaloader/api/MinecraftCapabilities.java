package dev.itsdropper.vantaloader.api;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Immutable snapshot of facilities verified by the active Minecraft adapter.
 *
 * <p>An empty capability set is intentional: a loader must not claim game hooks,
 * mappings, or interoperability merely because a Minecraft version string parsed.</p>
 */
public final class MinecraftCapabilities {
    private static final MinecraftCapabilities NONE =
            new MinecraftCapabilities(EnumSet.noneOf(MinecraftCapability.class));

    private final Set<MinecraftCapability> supported;

    private MinecraftCapabilities(EnumSet<MinecraftCapability> supported) {
        this.supported = Collections.unmodifiableSet(EnumSet.copyOf(supported));
    }

    public static MinecraftCapabilities none() {
        return NONE;
    }

    public static MinecraftCapabilities of(MinecraftCapability... capabilities) {
        if (capabilities == null || capabilities.length == 0) return NONE;
        EnumSet<MinecraftCapability> values = EnumSet.noneOf(MinecraftCapability.class);
        for (MinecraftCapability capability : capabilities) {
            if (capability == null) throw new NullPointerException("capability");
            values.add(capability);
        }
        return values.isEmpty() ? NONE : new MinecraftCapabilities(values);
    }

    public boolean supports(MinecraftCapability capability) {
        if (capability == null) throw new NullPointerException("capability");
        return supported.contains(capability);
    }

    public boolean supportsAll(MinecraftCapability... capabilities) {
        if (capabilities == null) throw new NullPointerException("capabilities");
        for (MinecraftCapability capability : capabilities) {
            if (!supports(capability)) return false;
        }
        return true;
    }

    public boolean supportsAny(MinecraftCapability... capabilities) {
        if (capabilities == null) throw new NullPointerException("capabilities");
        for (MinecraftCapability capability : capabilities) {
            if (supports(capability)) return true;
        }
        return false;
    }

    /** Returns an immutable set that cannot mutate the loader's capability snapshot. */
    public Set<MinecraftCapability> asSet() {
        return supported;
    }

    public boolean isEmpty() {
        return supported.isEmpty();
    }
}
