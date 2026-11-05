package dev.itsdropper.vantaloader.api;

/** Read-only official Minecraft name mappings available during transformation. */
public interface MinecraftMappingView {
    String runtimeClassName(String namedBinaryName);
    String namedClassName(String runtimeBinaryName);
    String runtimeMethodName(String ownerBinaryName, String namedMethodName, String namedDescriptor);
    String runtimeFieldName(String ownerBinaryName, String namedFieldName, String namedDescriptor);
}
