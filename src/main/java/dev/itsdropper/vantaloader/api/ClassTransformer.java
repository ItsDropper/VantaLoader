package dev.itsdropper.vantaloader.api;

/**
 * Transforms the bytecode of one exact target class.
 *
 * Implementations must return a valid class-file byte array. Returning null is
 * not supported; return the input unchanged when no modification is needed.
 */
@FunctionalInterface
public interface ClassTransformer {
    byte[] transform(ClassTransformContext context, byte[] originalClass) throws Exception;
}
