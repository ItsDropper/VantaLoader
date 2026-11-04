package dev.itsdropper.vantaloader.api;

/** Immutable metadata supplied to one class transformation. */
public record ClassTransformContext(
        String modId,
        String transformationId,
        String targetClassName
) { }
