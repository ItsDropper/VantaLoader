package dev.itsdropper.vantaloader.api;

/** Immutable metadata supplied to one class transformation. */
public record ClassTransformContext(
        String modId,
        String transformationId,
        String targetClassName,
        MinecraftMappingView mappings
) {
    /** Retains source compatibility for integrations constructing the original context shape. */
    public ClassTransformContext(String modId, String transformationId, String targetClassName) {
        this(modId, transformationId, targetClassName, MinecraftMappingViewIdentity.INSTANCE);
    }

    private enum MinecraftMappingViewIdentity implements MinecraftMappingView {
        INSTANCE;
        @Override public String runtimeClassName(String name) { return name; }
        @Override public String namedClassName(String name) { return name; }
        @Override public String runtimeDescriptor(String descriptor) { return descriptor; }
        @Override public String runtimeMethodName(String owner, String name, String descriptor) { return name; }
        @Override public String runtimeFieldName(String owner, String name, String descriptor) { return name; }
    }
}
