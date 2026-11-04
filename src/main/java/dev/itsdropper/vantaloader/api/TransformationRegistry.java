package dev.itsdropper.vantaloader.api;

/** Mod-scoped registration surface for deterministic class transformations. */
public interface TransformationRegistry {
    /**
     * Registers a transformation for an exact binary class name, such as
     * "net.minecraft.client.Minecraft". Lower priority values run first.
     * Registration IDs must be unique within the owning mod.
     */
    void register(String id, String targetClassName, int priority, ClassTransformer transformer);
}
